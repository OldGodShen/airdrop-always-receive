package io.github.oldgodshen.airdropalwaysreceive;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class RootLogReader {
    private static final String TARGET =
            "com.miui.mishare.connectivity";
    private static final String LOG =
            "/data/data/" + TARGET + "/files/airdrop-always-receive/airdrop-always-receive.log";

    private RootLogReader() {
    }

    static String read() {
        String direct = run(new String[]{"su", "-c", "cat " + LOG});
        if (!direct.isEmpty()) {
            return direct;
        }
        return run(new String[]{"run-as", TARGET, "cat", LOG});
    }

    private static String run(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
            }
            int exit = process.waitFor();
            return exit == 0 ? result.toString() : "";
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
