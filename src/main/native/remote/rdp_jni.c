#include <jni.h>
#include <openssl/provider.h>

#include <freerdp/client/cmdline.h>
#include <freerdp/client/channels.h>
#include <freerdp/client/disp.h>
#include <freerdp/addin.h>
#include <freerdp/codec/color.h>
#include <freerdp/freerdp.h>
#include <freerdp/gdi/gdi.h>
#include <winpr/synch.h>

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define COMMAND_CAPACITY 2048
#define NCAT_INPUT_MOUSE 1
#define NCAT_INPUT_WHEEL 2
#define NCAT_INPUT_KEY 3
#define NCAT_INPUT_TEXT 4
#define NCAT_INPUT_RESIZE 5

typedef struct {
    int kind;
    int a;
    int b;
    int c;
} RdpCommand;

typedef struct RdpSession RdpSession;

typedef struct {
    rdpContext base;
    RdpSession* owner;
} NcatContext;

struct RdpSession {
    CRITICAL_SECTION lock;
    RdpCommand commands[COMMAND_CAPACITY];
    int command_head;
    int command_count;
    int buttons;
    int pointer_x;
    int pointer_y;
    int target_width;
    int target_height;
    BOOL stop;
    BOOL connected;
    BOOL gdi_ready;
    BOOL display_ready;
    freerdp* instance;
    DispClientContext* display;
    JNIEnv* env;
    jobject java_session;
    jmethodID frame_method;
    jmethodID resize_method;
    jmethodID status_method;
    jmethodID certificate_method;
};

static RdpSession* from_handle(jlong handle) {
    return (RdpSession*)(uintptr_t)handle;
}

static RdpSession* owner_of(rdpContext* context) {
    return ((NcatContext*)context)->owner;
}

static void clear_exception(JNIEnv* env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }
}

static void status(RdpSession* session, const char* value) {
    JNIEnv* env = session->env;
    if (!env || !session->java_session || !session->status_method) {
        return;
    }
    jstring text = (*env)->NewStringUTF(env, value ? value : "error:Falha RDP");
    if (!text) {
        clear_exception(env);
        return;
    }
    (*env)->CallVoidMethod(env, session->java_session, session->status_method, text);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, text);
}

static void error_status(RdpSession* session, const char* reason) {
    char message[512];
    const char* value = reason && reason[0] ? reason : "Falha RDP";
    size_t length = strlen(value);
    if (length > sizeof(message) - 7) {
        length = sizeof(message) - 7;
    }
    memcpy(message, "error:", 6);
    memcpy(message + 6, value, length);
    message[length + 6] = '\0';
    status(session, message);
}

static BOOL is_stopping(RdpSession* session) {
    BOOL result;
    EnterCriticalSection(&session->lock);
    result = session->stop;
    LeaveCriticalSection(&session->lock);
    return result;
}

static void resized(RdpSession* session, int width, int height) {
    JNIEnv* env = session->env;
    if (!env || width <= 0 || height <= 0) {
        return;
    }
    (*env)->CallVoidMethod(env, session->java_session, session->resize_method,
                           (jint)width, (jint)height);
    clear_exception(env);
}

static BOOL emit_rectangle(RdpSession* session, rdpGdi* gdi, int x, int y, int width,
                           int height) {
    JNIEnv* env = session->env;
    if (!env || !gdi->primary_buffer || gdi->stride < (UINT32)gdi->width * 4) {
        return FALSE;
    }
    const int maximum_rows = width >= 2048 ? 128 : 512;
    for (int top = 0; top < height; top += maximum_rows) {
        int rows = height - top;
        if (rows > maximum_rows) {
            rows = maximum_rows;
        }
        jsize count = (jsize)(width * rows);
        jintArray frame = (*env)->NewIntArray(env, count);
        if (!frame) {
            clear_exception(env);
            return FALSE;
        }
        jint* pixels = (*env)->GetIntArrayElements(env, frame, NULL);
        if (!pixels) {
            (*env)->DeleteLocalRef(env, frame);
            clear_exception(env);
            return FALSE;
        }
        for (int row = 0; row < rows; ++row) {
            const BYTE* source = gdi->primary_buffer +
                                 (size_t)(y + top + row) * gdi->stride + (size_t)x * 4;
            jint* destination = pixels + (size_t)row * width;
            for (int column = 0; column < width; ++column) {
                const BYTE* pixel = source + (size_t)column * 4;
                destination[column] = (jint)(0xff000000u | ((uint32_t)pixel[2] << 16) |
                                             ((uint32_t)pixel[1] << 8) | pixel[0]);
            }
        }
        (*env)->ReleaseIntArrayElements(env, frame, pixels, 0);
        (*env)->CallVoidMethod(env, session->java_session, session->frame_method,
                               (jint)x, (jint)(y + top), (jint)width, (jint)rows, frame);
        BOOL failed = (*env)->ExceptionCheck(env);
        clear_exception(env);
        (*env)->DeleteLocalRef(env, frame);
        if (failed) {
            return FALSE;
        }
    }
    return TRUE;
}

static BOOL begin_paint(rdpContext* context) {
    rdpGdi* gdi = context->gdi;
    if (!gdi || !gdi->primary || !gdi->primary->hdc || !gdi->primary->hdc->hwnd ||
        !gdi->primary->hdc->hwnd->invalid) {
        return FALSE;
    }
    gdi->primary->hdc->hwnd->invalid->null = TRUE;
    return TRUE;
}

static BOOL end_paint(rdpContext* context) {
    RdpSession* session = owner_of(context);
    rdpGdi* gdi = context->gdi;
    if (!session || !gdi || !gdi->primary || !gdi->primary->hdc ||
        !gdi->primary->hdc->hwnd || !gdi->primary->hdc->hwnd->invalid) {
        return FALSE;
    }
    HGDI_RGN region = gdi->primary->hdc->hwnd->invalid;
    if (region->null) {
        return TRUE;
    }
    int x = region->x < 0 ? 0 : region->x;
    int y = region->y < 0 ? 0 : region->y;
    int right = region->x + region->w;
    int bottom = region->y + region->h;
    if (right > gdi->width) {
        right = gdi->width;
    }
    if (bottom > gdi->height) {
        bottom = gdi->height;
    }
    if (right <= x || bottom <= y) {
        return TRUE;
    }
    return emit_rectangle(session, gdi, x, y, right - x, bottom - y);
}

static BOOL desktop_resize(rdpContext* context) {
    RdpSession* session = owner_of(context);
    rdpGdi* gdi = context->gdi;
    UINT32 width = freerdp_settings_get_uint32(context->settings, FreeRDP_DesktopWidth);
    UINT32 height = freerdp_settings_get_uint32(context->settings, FreeRDP_DesktopHeight);
    if (!session || !gdi || !gdi_resize(gdi, width, height)) {
        return FALSE;
    }
    resized(session, (int)width, (int)height);
    return TRUE;
}

static UINT display_caps(DispClientContext* display, UINT32 maximum_monitors,
                         UINT32 area_a, UINT32 area_b) {
    (void)maximum_monitors;
    (void)area_a;
    (void)area_b;
    RdpSession* session = (RdpSession*)display->custom;
    if (session) {
        session->display_ready = TRUE;
    }
    return CHANNEL_RC_OK;
}

static void channel_connected(void* context, const ChannelConnectedEventArgs* event) {
    RdpSession* session = owner_of((rdpContext*)context);
    if (!session || !event || !event->name ||
        strcmp(event->name, DISP_DVC_CHANNEL_NAME) != 0) {
        return;
    }
    session->display = (DispClientContext*)event->pInterface;
    if (session->display) {
        session->display->custom = session;
        session->display->DisplayControlCaps = display_caps;
    }
}

static void channel_disconnected(void* context, const ChannelDisconnectedEventArgs* event) {
    RdpSession* session = owner_of((rdpContext*)context);
    if (!session || !event || !event->name ||
        strcmp(event->name, DISP_DVC_CHANNEL_NAME) != 0) {
        return;
    }
    session->display = NULL;
    session->display_ready = FALSE;
}

static BOOL pre_connect(freerdp* instance) {
    rdpContext* context = instance->context;
    if (PubSub_SubscribeChannelConnected(context->pubSub, channel_connected) < 0 ||
        PubSub_SubscribeChannelDisconnected(context->pubSub, channel_disconnected) < 0) {
        return FALSE;
    }
    return freerdp_client_load_addins(context->channels, context->settings);
}

static BOOL post_connect(freerdp* instance) {
    rdpContext* context = instance->context;
    RdpSession* session = owner_of(context);
    if (!gdi_init(instance, PIXEL_FORMAT_BGRA32)) {
        return FALSE;
    }
    session->gdi_ready = TRUE;
    context->update->BeginPaint = begin_paint;
    context->update->EndPaint = end_paint;
    context->update->DesktopResize = desktop_resize;
    resized(session, context->gdi->width, context->gdi->height);
    return TRUE;
}

static DWORD verify_certificate(freerdp* instance, const char* host, UINT16 port,
                                const char* common_name, const char* subject,
                                const char* issuer, const char* fingerprint, DWORD flags) {
    (void)host;
    (void)port;
    (void)common_name;
    (void)subject;
    (void)issuer;
    (void)flags;
    RdpSession* session = owner_of(instance->context);
    JNIEnv* env = session->env;
    jstring value = (*env)->NewStringUTF(env, fingerprint ? fingerprint : "");
    if (!value) {
        clear_exception(env);
        return 0;
    }
    jboolean accepted = (*env)->CallBooleanMethod(env, session->java_session,
                                                   session->certificate_method, value, JNI_FALSE);
    BOOL failed = (*env)->ExceptionCheck(env);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, value);
    return !failed && accepted ? 1 : 0;
}

static DWORD verify_changed_certificate(freerdp* instance, const char* host, UINT16 port,
                                        const char* common_name, const char* subject,
                                        const char* issuer, const char* fingerprint,
                                        const char* old_subject, const char* old_issuer,
                                        const char* old_fingerprint, DWORD flags) {
    (void)host;
    (void)port;
    (void)common_name;
    (void)subject;
    (void)issuer;
    (void)old_subject;
    (void)old_issuer;
    (void)old_fingerprint;
    (void)flags;
    RdpSession* session = owner_of(instance->context);
    JNIEnv* env = session->env;
    jstring value = (*env)->NewStringUTF(env, fingerprint ? fingerprint : "");
    if (!value) {
        clear_exception(env);
        return 0;
    }
    (*env)->CallBooleanMethod(env, session->java_session,
                              session->certificate_method, value, JNI_TRUE);
    clear_exception(env);
    (*env)->DeleteLocalRef(env, value);
    return 0;
}

static char* password_utf8(JNIEnv* env, jcharArray input) {
    jsize length = (*env)->GetArrayLength(env, input);
    jchar* utf16 = (jchar*)calloc((size_t)length + 1, sizeof(jchar));
    char* output = (char*)calloc((size_t)length * 3 + 1, 1);
    if (!utf16 || !output) {
        free(utf16);
        free(output);
        return NULL;
    }
    (*env)->GetCharArrayRegion(env, input, 0, length, utf16);
    if ((*env)->ExceptionCheck(env)) {
        clear_exception(env);
        memset(utf16, 0, ((size_t)length + 1) * sizeof(jchar));
        free(utf16);
        free(output);
        return NULL;
    }
    size_t at = 0;
    for (jsize i = 0; i < length; ++i) {
        uint32_t code = utf16[i];
        if (code >= 0xd800 && code <= 0xdbff && i + 1 < length &&
            utf16[i + 1] >= 0xdc00 && utf16[i + 1] <= 0xdfff) {
            code = 0x10000 + ((code - 0xd800) << 10) + (utf16[++i] - 0xdc00);
        } else if (code >= 0xd800 && code <= 0xdfff) {
            code = 0xfffd;
        }
        if (code < 0x80) {
            output[at++] = (char)code;
        } else if (code < 0x800) {
            output[at++] = (char)(0xc0 | (code >> 6));
            output[at++] = (char)(0x80 | (code & 0x3f));
        } else if (code < 0x10000) {
            output[at++] = (char)(0xe0 | (code >> 12));
            output[at++] = (char)(0x80 | ((code >> 6) & 0x3f));
            output[at++] = (char)(0x80 | (code & 0x3f));
        } else {
            output[at++] = (char)(0xf0 | (code >> 18));
            output[at++] = (char)(0x80 | ((code >> 12) & 0x3f));
            output[at++] = (char)(0x80 | ((code >> 6) & 0x3f));
            output[at++] = (char)(0x80 | (code & 0x3f));
        }
    }
    memset(utf16, 0, ((size_t)length + 1) * sizeof(jchar));
    free(utf16);
    return output;
}

static BOOL configure(freerdp* instance, const char* host, int port, const char* domain,
                      const char* user, const char* password, int width, int height) {
    rdpSettings* settings = instance->context->settings;
    return freerdp_settings_set_string(settings, FreeRDP_ServerHostname, host) &&
           freerdp_settings_set_uint32(settings, FreeRDP_ServerPort, (UINT32)port) &&
           freerdp_settings_set_string(settings, FreeRDP_Domain, domain) &&
           freerdp_settings_set_string(settings, FreeRDP_Username, user) &&
           freerdp_settings_set_string(settings, FreeRDP_Password, password) &&
           freerdp_settings_set_uint32(settings, FreeRDP_DesktopWidth, (UINT32)width) &&
           freerdp_settings_set_uint32(settings, FreeRDP_DesktopHeight, (UINT32)height) &&
           freerdp_settings_set_uint32(settings, FreeRDP_ColorDepth, 32) &&
           freerdp_settings_set_bool(settings, FreeRDP_AudioPlayback, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_AudioCapture, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_DeviceRedirection, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_NetworkAutoDetect, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_SupportHeartbeatPdu, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_SupportMultitransport, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_RedirectClipboard, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_SoftwareGdi, TRUE) &&
           freerdp_settings_set_bool(settings, FreeRDP_SupportGraphicsPipeline, FALSE) &&
           freerdp_settings_set_bool(settings, FreeRDP_SupportDisplayControl, TRUE) &&
           freerdp_settings_set_bool(settings, FreeRDP_DynamicResolutionUpdate, TRUE) &&
           freerdp_settings_set_bool(settings, FreeRDP_CertificateCallbackPreferPEM, FALSE);
}

static void request_resize(RdpSession* session, int width, int height) {
    session->target_width = width;
    session->target_height = height;
    if (!session->display || !session->display_ready || !session->display->SendMonitorLayout) {
        return;
    }
    DISPLAY_CONTROL_MONITOR_LAYOUT layout = { 0 };
    layout.Flags = DISPLAY_CONTROL_MONITOR_PRIMARY;
    layout.Width = (UINT32)width;
    layout.Height = (UINT32)height;
    layout.PhysicalWidth = (UINT32)(width * 254 / 960);
    layout.PhysicalHeight = (UINT32)(height * 254 / 960);
    layout.DesktopScaleFactor = 100;
    layout.DeviceScaleFactor = 100;
    if (session->display->SendMonitorLayout(session->display, 1, &layout) != CHANNEL_RC_OK) {
        error_status(session, "Servidor RDP recusou o redimensionamento");
    } else {
        session->target_width = 0;
        session->target_height = 0;
    }
}

static void send_mouse(RdpSession* session, rdpInput* input, int x, int y, int buttons) {
    UINT16 px = (UINT16)(x < 0 ? 0 : (x > 65535 ? 65535 : x));
    UINT16 py = (UINT16)(y < 0 ? 0 : (y > 65535 ? 65535 : y));
    static const int masks[3] = { 1, 2, 4 };
    static const UINT16 flags[3] = { PTR_FLAGS_BUTTON1, PTR_FLAGS_BUTTON3,
                                     PTR_FLAGS_BUTTON2 };
    freerdp_input_send_mouse_event(input, PTR_FLAGS_MOVE, px, py);
    for (int i = 0; i < 3; ++i) {
        if ((session->buttons & masks[i]) != (buttons & masks[i])) {
            UINT16 event = flags[i] | ((buttons & masks[i]) ? PTR_FLAGS_DOWN : 0);
            freerdp_input_send_mouse_event(input, event, px, py);
        }
    }
    session->buttons = buttons & 7;
    session->pointer_x = px;
    session->pointer_y = py;
}

static void send_wheel(RdpSession* session, rdpInput* input, int delta) {
    int steps = delta;
    if (steps > 32) {
        steps = 32;
    } else if (steps < -32) {
        steps = -32;
    }
    for (int i = 0; i < abs(steps); ++i) {
        UINT16 flags = PTR_FLAGS_WHEEL | 120;
        if (steps > 0) {
            flags |= PTR_FLAGS_WHEEL_NEGATIVE;
        }
        freerdp_input_send_mouse_event(input, flags, (UINT16)session->pointer_x,
                                       (UINT16)session->pointer_y);
    }
}

static void send_text(rdpInput* input, uint32_t code) {
    if (code < 0x10000) {
        freerdp_input_send_unicode_keyboard_event(input, 0, (UINT16)code);
        freerdp_input_send_unicode_keyboard_event(input, KBD_FLAGS_RELEASE, (UINT16)code);
    } else if (code <= 0x10ffff) {
        code -= 0x10000;
        UINT16 high = (UINT16)(0xd800 | (code >> 10));
        UINT16 low = (UINT16)(0xdc00 | (code & 0x3ff));
        freerdp_input_send_unicode_keyboard_event(input, 0, high);
        freerdp_input_send_unicode_keyboard_event(input, 0, low);
        freerdp_input_send_unicode_keyboard_event(input, KBD_FLAGS_RELEASE, low);
        freerdp_input_send_unicode_keyboard_event(input, KBD_FLAGS_RELEASE, high);
    }
}

static void process_commands(RdpSession* session) {
    rdpInput* input = session->instance->context->input;
    for (;;) {
        RdpCommand command;
        EnterCriticalSection(&session->lock);
        if (session->command_count == 0 || session->stop) {
            LeaveCriticalSection(&session->lock);
            return;
        }
        command = session->commands[session->command_head];
        session->command_head = (session->command_head + 1) % COMMAND_CAPACITY;
        --session->command_count;
        LeaveCriticalSection(&session->lock);

        switch (command.kind) {
            case NCAT_INPUT_MOUSE:
                send_mouse(session, input, command.a, command.b, command.c);
                break;
            case NCAT_INPUT_WHEEL:
                send_wheel(session, input, command.a);
                break;
            case NCAT_INPUT_KEY: {
                UINT16 flags = (command.a & 0x100) ? KBD_FLAGS_EXTENDED : 0;
                if (!command.b) {
                    flags |= KBD_FLAGS_RELEASE;
                }
                freerdp_input_send_keyboard_event(input, flags, (UINT8)(command.a & 0xff));
                break;
            }
            case NCAT_INPUT_TEXT:
                send_text(input, (uint32_t)command.a);
                break;
            case NCAT_INPUT_RESIZE:
                request_resize(session, command.a, command.b);
                break;
            default:
                break;
        }
    }
}

JNIEXPORT jstring JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_nativeVersion(JNIEnv* env,
                                                                           jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, freerdp_get_version_string());
}

JNIEXPORT jboolean JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_nativeConfigureCrypto(
    JNIEnv* env, jclass clazz, jstring java_path) {
    (void)clazz;
    const char* path = (*env)->GetStringUTFChars(env, java_path, NULL);
    if (!path) {
        clear_exception(env);
        return JNI_FALSE;
    }
    int configured = OSSL_PROVIDER_set_default_search_path(NULL, path);
    (*env)->ReleaseStringUTFChars(env, java_path, path);
    if (!configured) {
        return JNI_FALSE;
    }
    OSSL_PROVIDER* base = OSSL_PROVIDER_load(NULL, "default");
    OSSL_PROVIDER* legacy = OSSL_PROVIDER_load(NULL, "legacy");
    if (!base || !legacy) {
        if (base) OSSL_PROVIDER_unload(base);
        if (legacy) OSSL_PROVIDER_unload(legacy);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_00024Session_nativeCreate(
    JNIEnv* env, jobject self) {
    RdpSession* session = (RdpSession*)calloc(1, sizeof(RdpSession));
    if (!session) {
        return 0;
    }
    InitializeCriticalSection(&session->lock);
    session->java_session = (*env)->NewGlobalRef(env, self);
    jclass clazz = (*env)->GetObjectClass(env, self);
    if (!session->java_session || !clazz) {
        clear_exception(env);
        if (session->java_session) {
            (*env)->DeleteGlobalRef(env, session->java_session);
        }
        DeleteCriticalSection(&session->lock);
        free(session);
        return 0;
    }
    session->frame_method = (*env)->GetMethodID(env, clazz, "frame", "(IIII[I)V");
    session->resize_method = (*env)->GetMethodID(env, clazz, "resized", "(II)V");
    session->status_method = (*env)->GetMethodID(env, clazz, "status", "(Ljava/lang/String;)V");
    session->certificate_method = (*env)->GetMethodID(env, clazz, "certificate",
                                                       "(Ljava/lang/String;Z)Z");
    (*env)->DeleteLocalRef(env, clazz);
    if (!session->frame_method || !session->resize_method || !session->status_method ||
        !session->certificate_method) {
        clear_exception(env);
        (*env)->DeleteGlobalRef(env, session->java_session);
        DeleteCriticalSection(&session->lock);
        free(session);
        return 0;
    }
    return (jlong)(uintptr_t)session;
}

JNIEXPORT void JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_00024Session_nativeRun(
    JNIEnv* env, jobject self, jlong handle, jstring java_host, jint port, jstring java_domain,
    jstring java_user, jcharArray java_password, jint width, jint height) {
    (void)self;
    RdpSession* session = from_handle(handle);
    if (!session || is_stopping(session)) {
        return;
    }
    session->env = env;
    session->target_width = width;
    session->target_height = height;

    if (freerdp_register_addin_provider(freerdp_channels_load_static_addin_entry, 0) !=
        CHANNEL_RC_OK) {
        error_status(session, "Falha ao registrar canais FreeRDP");
        session->env = NULL;
        return;
    }

    const char* host = (*env)->GetStringUTFChars(env, java_host, NULL);
    const char* domain = (*env)->GetStringUTFChars(env, java_domain, NULL);
    const char* user = (*env)->GetStringUTFChars(env, java_user, NULL);
    char* password = password_utf8(env, java_password);
    if (!host || !domain || !user || !password) {
        clear_exception(env);
        error_status(session, "Memória insuficiente para abrir RDP");
        goto release_strings;
    }

    freerdp* instance = freerdp_new();
    if (!instance) {
        error_status(session, "Falha ao iniciar FreeRDP");
        goto release_strings;
    }
    instance->ContextSize = sizeof(NcatContext);
    instance->PreConnect = pre_connect;
    instance->PostConnect = post_connect;
    instance->VerifyCertificateEx = verify_certificate;
    instance->VerifyChangedCertificateEx = verify_changed_certificate;
    if (!freerdp_context_new(instance)) {
        error_status(session, "Falha ao criar contexto FreeRDP");
        freerdp_free(instance);
        goto release_strings;
    }
    ((NcatContext*)instance->context)->owner = session;
    EnterCriticalSection(&session->lock);
    session->instance = instance;
    BOOL stop = session->stop;
    LeaveCriticalSection(&session->lock);
    if (stop) {
        goto cleanup;
    }
    if (!configure(instance, host, port, domain, user, password, width, height)) {
        error_status(session, "Falha ao configurar sessão RDP");
        goto cleanup;
    }
    memset(password, 0, strlen(password));
    free(password);
    password = NULL;
    (*env)->ReleaseStringUTFChars(env, java_host, host);
    (*env)->ReleaseStringUTFChars(env, java_domain, domain);
    (*env)->ReleaseStringUTFChars(env, java_user, user);
    host = domain = user = NULL;

    if (!freerdp_connect(instance)) {
        if (!is_stopping(session)) {
            UINT32 code = freerdp_get_last_error(instance->context);
            error_status(session, freerdp_get_last_error_string(code));
        }
        goto cleanup;
    }
    session->connected = TRUE;
    status(session, "connected");
    HANDLE handles[64];
    while (!is_stopping(session) && !freerdp_shall_disconnect_context(instance->context)) {
        process_commands(session);
        DWORD count = freerdp_get_event_handles(instance->context, handles, 64);
        if (count == 0 || count > 64) {
            error_status(session, "Falha nos eventos RDP");
            break;
        }
        DWORD wait_result = WaitForMultipleObjects(count, handles, FALSE, 30);
        if (wait_result == WAIT_FAILED || !freerdp_check_event_handles(instance->context)) {
            if (!is_stopping(session)) {
                UINT32 code = freerdp_get_last_error(instance->context);
                error_status(session, freerdp_get_last_error_string(code));
            }
            break;
        }
        if (session->display && session->display_ready && session->target_width > 0 &&
            session->target_height > 0) {
            int target_width = session->target_width;
            int target_height = session->target_height;
            request_resize(session, target_width, target_height);
        }
    }

cleanup:
    if (session->connected) {
        freerdp_disconnect(instance);
        status(session, "disconnected");
    }
    if (session->gdi_ready) {
        gdi_free(instance);
        session->gdi_ready = FALSE;
    }
    EnterCriticalSection(&session->lock);
    session->instance = NULL;
    LeaveCriticalSection(&session->lock);
    freerdp_context_free(instance);
    freerdp_free(instance);

release_strings:
    if (password) {
        memset(password, 0, strlen(password));
        free(password);
    }
    if (host) {
        (*env)->ReleaseStringUTFChars(env, java_host, host);
    }
    if (domain) {
        (*env)->ReleaseStringUTFChars(env, java_domain, domain);
    }
    if (user) {
        (*env)->ReleaseStringUTFChars(env, java_user, user);
    }
    session->env = NULL;
}

JNIEXPORT jboolean JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_00024Session_nativeQueue(
    JNIEnv* env, jobject self, jlong handle, jint kind, jint a, jint b, jint c) {
    (void)env;
    (void)self;
    RdpSession* session = from_handle(handle);
    if (!session) {
        return JNI_FALSE;
    }
    EnterCriticalSection(&session->lock);
    if (session->stop || session->command_count == COMMAND_CAPACITY) {
        LeaveCriticalSection(&session->lock);
        return JNI_FALSE;
    }
    int tail = (session->command_head + session->command_count) % COMMAND_CAPACITY;
    session->commands[tail] = (RdpCommand){ kind, a, b, c };
    ++session->command_count;
    LeaveCriticalSection(&session->lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_00024Session_nativeStop(
    JNIEnv* env, jobject self, jlong handle) {
    (void)env;
    (void)self;
    RdpSession* session = from_handle(handle);
    if (!session) {
        return;
    }
    EnterCriticalSection(&session->lock);
    session->stop = TRUE;
    if (session->instance && session->instance->context) {
        freerdp_abort_connect_context(session->instance->context);
    }
    LeaveCriticalSection(&session->lock);
}

JNIEXPORT void JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_00024Session_nativeDestroy(
    JNIEnv* env, jobject self, jlong handle) {
    (void)self;
    RdpSession* session = from_handle(handle);
    if (!session) {
        return;
    }
    (*env)->DeleteGlobalRef(env, session->java_session);
    DeleteCriticalSection(&session->lock);
    memset(session, 0, sizeof(RdpSession));
    free(session);
}
