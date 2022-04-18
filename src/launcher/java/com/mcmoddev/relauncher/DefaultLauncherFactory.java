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
import com.mcmoddev.relauncher.api.LauncherFactory;
import com.mcmoddev.relauncher.discord.DefaultDiscordIntegration;
import com.mcmoddev.relauncher.github.GithubRelease;
import com.mcmoddev.relauncher.github.GithubUpdateChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class DefaultLauncherFactory implements LauncherFactory<Config> {

    public static final Pattern SELF_UPDATE_PATTERN = Pattern.compile("ReLauncher-.*-all\\.jar", Pattern.CASE_INSENSITIVE);

    @Override
    public @NotNull Config getConfig(final Path path) {
        try {
            return Config.load(path, Config.class, Config.DEFAULT);
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull JarUpdater createUpdater(final Config config) {
        final var checker = new GithubUpdateChecker(config.gitHub.owner, config.gitHub.repo, HttpClient.newBuilder()
            .executor(Main.HTTP_CLIENT_EXECUTOR)
            .build(),
            Pattern.compile(config.checkingInfo.filePattern));
        return new DefaultJarUpdater(Path.of(config.jarPath), checker, config.jvmArgs, config.discord.loggingWebhook);
    }

    @Override
    public @Nullable DiscordIntegration createDiscordIntegration(final Config config, final JarUpdater updater) {
        return new DefaultDiscordIntegration(Path.of(""), config.discord, () -> updater);
    }

    @Override
    public @Nullable String getSelfUpdateUrl(final String tagName) throws IOException, InterruptedException {
        final var repo = getLauncherRepo();
        if (repo == null) {
            return null;
        }
        final var url = "https://api.github.com/repos/%s/%s/releases/tags/%s".formatted(repo.owner(), repo.repo(), tagName);
        final var response = HttpClient.newBuilder()
            .executor(Main.HTTP_CLIENT_EXECUTOR)
            .build()
            .send(HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        final var release = Constants.GSON.fromJson(response.body(), GithubRelease.class);
        return release.assets.stream()
            .filter(a -> SELF_UPDATE_PATTERN.matcher(a.name).find())
            .map(a -> a.browserDownloadUrl)
            .findFirst()
            .orElse(null);
    }
}
