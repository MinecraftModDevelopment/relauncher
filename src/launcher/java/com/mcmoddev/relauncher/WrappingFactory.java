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
package com.mcmoddev.relauncher;

import com.mcmoddev.relauncher.api.DiscordIntegration;
import com.mcmoddev.relauncher.api.JarUpdater;
import com.mcmoddev.relauncher.api.LauncherConfig;
import com.mcmoddev.relauncher.api.LauncherFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

record WrappingFactory<T extends LauncherConfig>(LauncherFactory<T> delegate) implements LauncherFactory<T> {

    @Override
    public @NotNull T getConfig(final Path path) {
        return delegate.getConfig(path);
    }

    @Override
    public @NotNull JarUpdater createUpdater(final T config) {
        return delegate.createUpdater(config);
    }

    public JarUpdater createUpdater(final Object config) {
        return delegate.createUpdater((T) config);
    }

    @Override
    public @Nullable DiscordIntegration createDiscordIntegration(final T config, final JarUpdater updater) {
        return delegate.createDiscordIntegration(config, updater);
    }

    public @Nullable DiscordIntegration createDiscordIntegration(final Object config, final JarUpdater updater) {
        return delegate.createDiscordIntegration((T) config, updater);
    }

    @Override
    public @Nullable RepoInfo getLauncherRepo() {
        return delegate.getLauncherRepo();
    }

    @Override
    public @Nullable String getSelfUpdateUrl(final String tagName) throws IOException, InterruptedException {
        return delegate.getSelfUpdateUrl(tagName);
    }
}
