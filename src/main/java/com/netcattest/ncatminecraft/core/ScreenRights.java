/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.core;

public abstract class ScreenRights {
    public static final int CHANGE_URL = 1;
    @Deprecated(forRemoval = true)
    public static final int CLICK = 2;
    public static final int INTERACT = 2;
    public static final int MANAGE_FRIEND_LIST = 4;
    public static final int MANAGE_OTHER_RIGHTS = 8;
    public static final int MANAGE_UPGRADES = 16;
    @Deprecated(forRemoval = true)
    public static final int CHANGE_RESOLUTION = 32;
    public static final int MODIFY_SCREEN = 32;

    public static final int NONE = 0;
    public static final int ALL = 0xFF;
    public static final int DEFAULTS = CHANGE_URL | INTERACT | MANAGE_UPGRADES | MODIFY_SCREEN;
}
