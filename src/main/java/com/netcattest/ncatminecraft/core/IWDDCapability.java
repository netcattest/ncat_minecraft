/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.core;

public interface IWDDCapability {
    boolean isFirstRun();
    void clearFirstRun();
    void cloneTo(IWDDCapability dst);
}
