/*
 * ReLauncher - https://github.com/MinecraftModDevelopment/ReLauncher
 * Copyright (C) 2016-2022 <MMD - MinecraftModDevelopment>
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

public class SelfUpdate {

    public static void main(String[] args) throws IOException, InterruptedException {
        Thread.sleep(1000 * 3); // Sleep 3 seconds
        final var jarPath = Path.of(args[0]).toAbsolutePath();
        final var relaunchCmd = args[1].replace("java  -jar", "java -jar");
        final var url = args[2];

        final var parent = jarPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(jarPath);

        try (final var is = new BufferedInputStream(new URL(url).openStream())) {
            Files.copy(is, jarPath);
            System.out.println(colour("Update to '" + url + "' successful! Re-starting the launcher..."));

            new ProcessBuilder(resolveScript(relaunchCmd))
                .inheritIO()
                .start();

            System.exit(0);
        }
    }

    public static String colour(String text) {
        return "\033[94;1m==== \033[36;1m" + text
            + " \033[94;1m====\033[0m";
    }

    public static String readAllLines(InputStream is) throws IOException {
        return new String(is.readAllBytes());
    }

    public static List<String> resolveScript(String script) throws IOException {
        final var directory = Path.of(".relauncher");
        var scriptFull = readAllLines(Objects.requireNonNull(SelfUpdate.class.getResourceAsStream("/relauncher-restart")));
        final Path path;
        final List<String> cmd;
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            path = directory.resolve("relaunch.bat").toAbsolutePath();
            scriptFull = scriptFull.formatted("cmd /c " + script);
            cmd = List.of(path.toString());
        } else {
            path = directory.resolve("relaunch.sh").toAbsolutePath();
            scriptFull = scriptFull.formatted(script);
            cmd = List.of("sh", path.toString());
        }
        Files.copy(new ByteArrayInputStream(scriptFull.getBytes(StandardCharsets.UTF_8)), path, StandardCopyOption.REPLACE_EXISTING);
        final var atView = Files.getFileAttributeView(path, DosFileAttributeView.class);
        atView.setHidden(true);
        return cmd;
    }

}
