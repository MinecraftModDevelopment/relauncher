/*
 * ReLauncher - https://github.com/MinecraftModDevelopment/ReLauncher
 * Copyright (C) 2016-2024 <MMD - MinecraftModDevelopment>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * Specifically version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 */
package com.mcmoddev.relauncher.selfupdate;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class SelfUpdate {

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        final var jarPath = Path.of(args[1]).toAbsolutePath();
        final var relaunchCmd = args[2].replace("java  -jar", "java -jar");
        final var url = args[3];
        final var launcherPId = Long.parseLong(args[0]);
        final var launcherProcess = ProcessHandle.of(launcherPId);
        if (launcherProcess.isPresent()) {
            final var handle = launcherProcess.get();
            if (handle.isAlive()) {
                handle.onExit().get(); // Await the launcher exit
            }
        }
        doUpdate(jarPath, relaunchCmd, url);
    }

    public static void doUpdate(Path jarPath, String relaunchCmd, String url) throws IOException {
        final var parent = jarPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(jarPath);

        try (final var is = new BufferedInputStream(new URL(url).openStream())) {
            Files.copy(is, jarPath);
            System.out.println(colour("Update to '" + url + "' successful! Re-starting the launcher..."));

            final var os = getOS();
            final var scriptPath = resolveScriptPath(os);
            final var restartCmd = resolveScript(getOS(), relaunchCmd);
            if (!os.contains("win")) { // Not windows, so we need to make the file executable
                final var process = new ProcessBuilder("chmod", "+x", scriptPath.toString())
                        .inheritIO()
                        .start();
                process.onExit().whenComplete(($, $$) -> {
                    try {
                        new ProcessBuilder(restartCmd)
                                .inheritIO()
                                .start();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    System.exit(0);
                });
            } else {
                new ProcessBuilder(restartCmd)
                        .inheritIO()
                        .start();
                System.exit(0);
            }
        }
    }

    public static String colour(String text) {
        return "\033[94;1m==== \033[36;1m" + text
                + " \033[94;1m====\033[0m";
    }

    public static String readAllLines(InputStream is) throws IOException {
        return new String(is.readAllBytes());
    }

    public static Path resolveScriptPath(String os) {
        final var directory = Path.of(".relauncher");
        return os.contains("win") ? directory.resolve("relaunch.bat").toAbsolutePath() : directory.resolve("relaunch.sh").toAbsolutePath();
    }

    public static List<String> resolveScript(String os, String script) throws IOException {
        var scriptFull = readAllLines(Objects.requireNonNull(SelfUpdate.class.getResourceAsStream("/relauncher-restart")));
        final Path path = resolveScriptPath(os);
        final List<String> cmd;
        if (os.contains("win")) {
            scriptFull = scriptFull.formatted("cmd /c " + script);
            cmd = List.of(path.toString());
        } else {
            scriptFull = scriptFull.formatted(script);
            scriptFull = "#!/usr/bin/env sh\n" + scriptFull;
            cmd = List.of("sh", path.toString());
        }
        Files.copy(new ByteArrayInputStream(scriptFull.getBytes(StandardCharsets.UTF_8)), path, StandardCopyOption.REPLACE_EXISTING);
        if (os.contains("win")) {
            final var atView = Files.getFileAttributeView(path, DosFileAttributeView.class);
            atView.setHidden(true);
        }
        return cmd;
    }

    private static String getOS() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT);
    }
}
