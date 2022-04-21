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
import com.mcmoddev.relauncher.api.connector.ProcessConnector;
import net.dv8tion.jda.api.utils.AllowedMentions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class Main {

    /**
     * The launcher's current version.
     *
     * <p>
     * The version will be taken from the {@code Implementation-Version} attribute of the JAR manifest. If that is
     * unavailable, the version shall be the combination of the string {@code "DEV "} and the current date and time
     * in {@link java.time.format.DateTimeFormatter#ISO_OFFSET_DATE_TIME}.
     */
    public static final String VERSION;

    static {
        var version = Main.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "DEV " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC));
        }
        VERSION = version;
    }

    public static final ThreadGroup THREAD_GROUP = new ThreadGroup("ReLauncher");

    public static final WrappingFactory FACTORY = new WrappingFactory<>(ServiceLoader.load(LauncherFactory.class)
        .findFirst()
        .orElse(new DefaultLauncherFactory()));
    public static final String RMI_NAME = ProcessConnector.BASE_NAME + "#" + (int) ProcessHandle.current().pid();
    public static final Logger LOG = LoggerFactory.getLogger("ReLauncher");
    public static final Path RELAUNCHER_DIR = Path.of(".relauncher");
    public static final Path CONFIG_PATH = FACTORY.getConfigPath(RELAUNCHER_DIR);
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

    private static LauncherConfig config;
    private static JarUpdater updater;
    private static DiscordIntegration discordIntegration;

    public static void main(String[] args) throws IOException {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        if (!Files.exists(RELAUNCHER_DIR)) {
            Files.createDirectories(RELAUNCHER_DIR);
        }

        final var cfgExists = Files.exists(CONFIG_PATH);
        config = FACTORY.getConfig(CONFIG_PATH);
        if (!cfgExists && config.throwIfNew()) {
            throw new RuntimeException("A new configuration file was created! Please configure it.");
        }

        updater = FACTORY.createUpdater(config);

        try {
            copyAgent(updater);
        } catch (IOException e) {
            LOG.error("Exception copying agent JAR: ", e);
            throw new RuntimeException(e);
        }

        if (config.isDiscordIntegrationEnabled()) {
            discordIntegration = FACTORY.createDiscordIntegration(config, updater);
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (discordIntegration != null) {
                discordIntegration.shutdown();
            }
        }));
    }

    @Nullable
    public static DiscordIntegration getDiscordIntegration() {
        return discordIntegration;
    }

    public static void copyAgent(JarUpdater updater) throws IOException {
        final var agentPath = updater.getAgentPath();
        Files.copy(updater.getAgentResource(), agentPath, StandardCopyOption.REPLACE_EXISTING);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            final var atView = Files.getFileAttributeView(agentPath, DosFileAttributeView.class);
            atView.setHidden(true);
        }
    }

    public static void selfUpdate(String tagName) throws Exception {
        if (!config.allowSelfUpdate()) {
            throw new Exception("Self Updating is turned off!");
        }

        final var jarPath = Main.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI()
            .getPath()
            .substring(1); // remove the starting '/'

        var command = getCommandLine(ProcessHandle.current())
            .orElseThrow(() -> new Exception("Could not determine the command to use for restarting!"));

        final var url = FACTORY.getSelfUpdateUrl(tagName);
        if (url == null) {
            throw new Exception("Unknown tag: " + tagName);
        }

        final var uri = URI.create(url);
        final var request = HttpRequest.newBuilder(uri)
            .GET()
            .header("accept", "application/vnd.github.v3+json")
            .build();

        final var res = HttpClient.newBuilder()
            .executor(HTTP_CLIENT_EXECUTOR)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            throw new Exception("Unknown tag: " + tagName);
        }

        final var selfUpdateJarPath = RELAUNCHER_DIR.resolve("selfupdate.jar").toAbsolutePath();
        Files.copy(Objects.requireNonNull(Main.class.getResourceAsStream("/relauncher-selfupdate.zip")), selfUpdateJarPath, StandardCopyOption.REPLACE_EXISTING);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            final var atView = Files.getFileAttributeView(selfUpdateJarPath, DosFileAttributeView.class);
            atView.setHidden(true);
        }

        final var process = updater.getProcess();
        if (process != null) {
            process.process().destroy();
        }

        new ProcessBuilder()
            .command(
                findJavaBinary(), "-jar", selfUpdateJarPath.toString(),
                jarPath,
                command,
                url
            )
            .inheritIO()
            .start();

        System.exit(0);
    }

    public static String findJavaBinary() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    /**
     * Returns the full command-line of the process.
     * <p>
     * This is a workaround for
     * <a href="https://stackoverflow.com/a/46768046/14731">https://stackoverflow.com/a/46768046/14731</a>
     *
     * @param processHandle a process handle
     * @return the command-line of the process
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static Optional<String> getCommandLine(ProcessHandle processHandle) throws UncheckedIOException {
        final var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return processHandle.info().commandLine();
        }
        final var desiredProcessId = processHandle.pid();
        try {
            final var process = new ProcessBuilder("wmic", "process", "where", "ProcessID=" + desiredProcessId, "get",
                "commandline", "/format:list").
                redirectErrorStream(true).
                start();
            try (final var inputStreamReader = new InputStreamReader(process.getInputStream());
                 final var reader = new BufferedReader(inputStreamReader)) {
                while (true) {
                    final var line = reader.readLine();
                    if (line == null) {
                        return Optional.empty();
                    }
                    if (!line.startsWith("CommandLine=")) {
                        continue;
                    }
                    return Optional.of(line.substring("CommandLine=".length()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Main() {
        throw new UnsupportedOperationException("Cannot instantiate a utility class");
    }
}
