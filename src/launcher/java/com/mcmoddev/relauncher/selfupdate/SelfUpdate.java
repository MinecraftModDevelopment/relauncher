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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
            final var process = Runtime.getRuntime()
                .exec(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "cmd /c " : "" + relaunchCmd,
                    null,
                    parent == null ? Path.of("").toFile() : parent.toFile()
                );

            try (final var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                // Redirect console
                // TODO find a better way to inherit IO, or use ProcessBuilder and find a way to split the command
                String line;
                while (process.isAlive() && (line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            System.exit(0);
        }
    }

    public static String colour(String text) {
        return "\033[94;1m==== \033[36;1m" + text
            + " \033[94;1m====\033[0m";
    }


}
