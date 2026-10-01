package com.robot.platform.ai.memory.provider;

import java.util.Locale;
import java.util.Set;

public final class MemoryModes {
    private MemoryModes() { }
    public static final Set<String> MODES = Set.of("NONE", "SESSION", "LONG_TERM", "NOMEM", "MEM_LOCAL_SHORT", "MEM0AI", "POWERMEM");
    public static String provider(String mode) {
        return switch (mode == null ? "NONE" : mode.trim().toUpperCase(Locale.ROOT)) {
            case "LONG_TERM" -> "mysql";
            case "MEM_LOCAL_SHORT" -> "mem_local_short";
            case "MEM0AI" -> "mem0ai";
            case "POWERMEM" -> "powermem";
            case "NONE", "SESSION", "NOMEM" -> "nomem";
            default -> throw new IllegalArgumentException("Unsupported memory provider");
        };
    }
    public static boolean persistent(String mode) { return !provider(mode).equals("nomem"); }
}
