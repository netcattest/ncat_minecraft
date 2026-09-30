package com.netcattest.ncatminecraft.utilities.browser.handlers.js;

import com.netcattest.ncatminecraft.utilities.browser.handlers.DisplayHandler;

import java.io.InputStream;
import java.lang.reflect.Field;

public class Scripts {
    private static int index = 1;

    @FileName("assets/ncat_minecraft/js/pointer_lock.js")
    public static final String POINTER_LOCK = get();
    @FileName("assets/ncat_minecraft/js/mouse_event.js")
    public static final String MOUSE_EVENT = get();
    @FileName("assets/ncat_minecraft/js/query_element.js")
    public static final String QUERY_ELEMENT = get();

    private static String get() {
        Field field = Scripts.class.getDeclaredFields()[index++];
        FileName name = field.getAnnotation(FileName.class);

        String text;
        try {
            InputStream is = DisplayHandler.class.getClassLoader().getResourceAsStream(name.value());
            text = new String(is.readAllBytes());
            is.close();
        } catch (Throwable err) {
            throw new RuntimeException(err);
        }

        return text;
    }
}
