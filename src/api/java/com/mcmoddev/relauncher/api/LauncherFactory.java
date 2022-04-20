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
package com.mcmoddev.relauncher.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

/**
 * A factory for ReLauncher. <br>
 * {@link java.util.ServiceLoader Service loaders} are used for finding the first factory.
 * The service path is {@code com.mcmoddev.relauncher.api.LauncherFactory}. <br>
 * If no factory is found, the default one will be used.
 *
 * @param <T> the type of the launcher config
 */
public interface LauncherFactory<T extends LauncherConfig> {

    /**
     * Gets a config from its path.
     *
     * @param path the path of the config
     * @return the config
     */
    @NotNull
    T getConfig(Path path);

    /**
     * Gets the path of the config file, relative to the launcher's directory.
     *
     * @param launcherDirectory the directory of the launcher
     * @return the config path
     */
    default Path getConfigPath(Path launcherDirectory) {
        return launcherDirectory.resolve("config.conf");
    }

    /**
     * Creates a {@link JarUpdater updater}.
     *
     * @param config the launcher config
     * @return the updater
     */
    @NotNull
    JarUpdater createUpdater(T config);

    /**
     * Creates a {@link DiscordIntegration}. <br>
     * The launcher only calls this method if Discord Integration is enabled in the config.
     *
     * @param config  the launcher config
     * @param updater the {@link JarUpdater jar updater}
     * @return the integration instance. Can be {@code null}.
     */
    @Nullable
    DiscordIntegration createDiscordIntegration(T config, JarUpdater updater);

    /**
     * @return the GitHub repository of the launcher
     */
    @Nullable
    default RepoInfo getLauncherRepo() {
        return new RepoInfo("MinecraftModDevelopment", "ReLauncher");
    }

    /**
     * Gets the self update url for a tag name.
     *
     * @param tagName the tag to update to
     * @return the download url, or {@code null}
     */
    @Nullable
    String getSelfUpdateUrl(String tagName) throws IOException, InterruptedException;

    record RepoInfo(String owner, String repo) {}
}