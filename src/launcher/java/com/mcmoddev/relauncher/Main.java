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
import com.mcmoddev.relauncher.api.connector.ProcessConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class Main {
    public static final ThreadGroup THREAD_GROUP = new ThreadGroup("ReLauncher");

    public static final String RMI_NAME = ProcessConnector.BASE_NAME + "#" + (int) ProcessHandle.current().pid();
    public static final Logger LOG = LoggerFactory.getLogger("ReLauncher");
    public static final Path RELAUNCHER_DIR = Path.of(".relauncher");
    public static final Path CONFIG_PATH = RELAUNCHER_DIR.resolve("config.conf");
    public static final Path AGENT_PATH = RELAUNCHER_DIR.resolve("agent.jar");
    public static final ScheduledThreadPoolExecutor SERVICE;

    static {
        final var service = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, r -> new Thread(THREAD_GROUP, r, "UpdatingLauncher"));
        service.setKeepAliveTime(1, TimeUnit.HOURS);
        SERVICE = service;
    }

    public static final ExecutorService HTTP_CLIENT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        final var thread = new Thread(THREAD_GROUP, r, "ReLauncherHttpClient");
        thread.setDaemon(true);
        return thread;
    });

    private static DiscordIntegration discordIntegration;

    public static void main(String[] args) throws IOException {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        if (!Files.exists(RELAUNCHER_DIR)) {
            Files.createDirectories(RELAUNCHER_DIR);
        }

        final var factory = resolveFactory();

        final var cfgExists = Files.exists(CONFIG_PATH);
        final var config = factory.getConfig(CONFIG_PATH);
        if (!cfgExists && config.throwIfNew()) {
            throw new RuntimeException("A new configuration file was created! Please configure it.");
        }

        final var updater = factory.createUpdater(config);

        try {
            copyAgent(updater);
        } catch (IOException e) {
            LOG.error("Exception copying agent JAR: ", e);
            throw new RuntimeException(e);
        }

        if (config.isDiscordIntegrationEnabled()) {
            discordIntegration = factory.createDiscordIntegration(config, updater);
            if (discordIntegration != null) {
                LOG.warn("Discord integration is active!");
                SERVICE.setMaximumPoolSize(2);
            }
        }

        final var checkingRate = config.getCheckingRate();

        if (checkingRate.amount() > -1) {
            SERVICE.scheduleAtFixedRate(updater, 0, checkingRate.amount(), checkingRate.unit());
            LOG.warn("Scheduled updater. Will run every {} minutes.", checkingRate);
        } else {
            updater.tryFirstStart();
            SERVICE.allowCoreThreadTimeOut(true);
        }
    }

    @Nullable
    public static DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }

    public static void copyAgent(JarUpdater updater) throws IOException {
        Files.copy(updater.getAgentResource(), updater.getAgentPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @NotNull
    static WrappingFactory resolveFactory() {
        return new WrappingFactory<>(ServiceLoader.load(LauncherFactory.class).findFirst().orElse(new DefaultLauncherFactory()));
    }
}
