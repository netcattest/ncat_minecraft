package com.netcattest.ncatminecraft.entity;

import java.util.Locale;

public enum RackModuleType {
    BROWSER(1, 2),
    SSH(1, 2),
    LOG(1, 1),
    DEVTOOLS(1, 1),
    SFTP(1, 1),
    TERMINAL(1, 2),
    REMOTE(1, 2),
    PROXY(1, 2),
    SWITCH(2, 8),
    MANAGED_SWITCH(2, 8);

    private final int heightU;
    private final int portCount;

    RackModuleType(int heightU, int portCount) {
        this.heightU = heightU;
        this.portCount = portCount;
    }

    public int heightU() {
        return heightU;
    }

    public int portCount() {
        return portCount;
    }

    public boolean isSwitch() {
        return this == SWITCH || this == MANAGED_SWITCH;
    }

    public String registryName() {
        return "rack_module_" + name().toLowerCase(Locale.ROOT);
    }
}
