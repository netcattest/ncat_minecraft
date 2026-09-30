/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.core;

import java.util.Map;

public interface IComputerArgs {
    String checkString(int i);
    int checkInteger(int i);
    Map checkTable(int i);
    int count();
}
