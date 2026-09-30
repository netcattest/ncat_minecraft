#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <stdlib.h>

JNIEXPORT jboolean JNICALL
Java_com_netcattest_ncatminecraft_client_remote_RdpNative_nativePrepareDirectory(
    JNIEnv* env, jclass clazz, jstring java_path) {
    (void)clazz;
    if (!java_path) {
        return JNI_FALSE;
    }
    const jchar* path = (*env)->GetStringChars(env, java_path, NULL);
    if (!path) {
        return JNI_FALSE;
    }
    jsize length = (*env)->GetStringLength(env, java_path);
    WCHAR* terminated = (WCHAR*)calloc((size_t)length + 1, sizeof(WCHAR));
    if (!terminated) {
        (*env)->ReleaseStringChars(env, java_path, path);
        return JNI_FALSE;
    }
    CopyMemory(terminated, path, (size_t)length * sizeof(WCHAR));
    BOOL configured = SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    DLL_DIRECTORY_COOKIE cookie = configured ? AddDllDirectory(terminated) : NULL;
    BOOL provider_path = cookie ? SetEnvironmentVariableW(L"OPENSSL_MODULES", terminated) : FALSE;
    if (provider_path) {
        provider_path = _wputenv_s(L"OPENSSL_MODULES", terminated) == 0;
    }
    free(terminated);
    (*env)->ReleaseStringChars(env, java_path, path);
    return cookie && provider_path ? JNI_TRUE : JNI_FALSE;
}
