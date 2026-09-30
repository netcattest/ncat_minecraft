/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.miniserv;

public enum PacketID {

    INIT_CONN,
    AUTHENTICATE,
    PING,
    BEGIN_FILE_UPLOAD,
    FILE_PART,
    FILE_STATUS,
    GET_FILE,
    QUOTA,
    LIST,
    DELETE;

    public static PacketID fromInt(int i) {
        PacketID[] values = values();
        return (i < 0 || i >= values.length) ? null : values[i];
    }

}
