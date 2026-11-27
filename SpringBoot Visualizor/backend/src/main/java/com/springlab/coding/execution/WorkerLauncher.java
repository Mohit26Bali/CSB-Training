package com.springlab.coding.execution;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Starts the worker client process (for WSL: {@code wsl.exe}, which starts the Linux driver). Its only job is to start a command
 * line that the backend built from fixed, validated parts, with an environment that holds none of the backend's own settings.
 * Tests replace it with a fake worker.
 */
@FunctionalInterface
interface WorkerLauncher {

    Process start(List<String> command) throws IOException;

    /**
     * Environment variables the Windows client genuinely needs to start. Everything else, including JWT_SECRET, DB_PASSWORD,
     * ADMIN_PASSWORD and any other setting of this application, is dropped; a name is kept only if it is on this list.
     */
    Set<String> ENVIRONMENT_ALLOW_LIST = Set.of("systemroot", "systemdrive", "windir", "path", "temp", "tmp", "userprofile", "localappdata",
            "appdata", "programdata", "programfiles", "programfiles(x86)", "home", "lang");

    static Map<String, String> minimalEnvironment(Map<String, String> parent) {
        Map<String, String> kept = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : parent.entrySet()) {
            if (ENVIRONMENT_ALLOW_LIST.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                kept.put(e.getKey(), e.getValue());
            }
        }
        return kept;
    }

    /** The real launcher. stderr is kept separate from stdout: stdout carries the single JSON answer and nothing else. */
    static WorkerLauncher processBuilder() {
        return command -> {
            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            Map<String, String> keep = minimalEnvironment(Map.copyOf(env));
            env.clear();
            env.putAll(keep);
            return pb.start();
        };
    }
}
