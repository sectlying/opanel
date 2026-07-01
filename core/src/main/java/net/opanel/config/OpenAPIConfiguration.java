package net.opanel.config;

import java.util.HashMap;

public class OpenAPIConfiguration {
    public static final String[] DEFAULT_INTERFACE_NAMES = new String[] {
        "info",
        "monitor",
        "plugins",
        "players",
        "logs"
    };

    public boolean enabled;
    public HashMap<String, Boolean> interfaces;

    public OpenAPIConfiguration(boolean enabled, HashMap<String, Boolean> interfaces) {
        this.enabled = enabled;
        this.interfaces = interfaces;
    }

    public static HashMap<String, Boolean> createDefaultInterfaces() {
        HashMap<String, Boolean> interfaces = new HashMap<>();
        for(String interfaceName : DEFAULT_INTERFACE_NAMES) {
            interfaces.put(interfaceName, true);
        }

        return interfaces;
    }

    public static boolean isValidInterfaceName(String interfaceName) {
        for(String defaultInterfaceName : DEFAULT_INTERFACE_NAMES) {
            if(defaultInterfaceName.equals(interfaceName)) return true;
        }

        return false;
    }
}
