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
package com.mcmoddev.relauncher;

import com.mcmoddev.relauncher.api.CustomScriptManager;
import com.mcmoddev.relauncher.api.DiscordIntegration;
import com.mcmoddev.relauncher.api.ProcessInfo;
import com.mcmoddev.relauncher.api.Release;
import com.mcmoddev.relauncher.api.connector.ProcessConnector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.mcmoddev.relauncher.Main.findJavaBinary;

public class DefaultScriptManager implements CustomScriptManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("ScriptManager");

    private final List<String> script;
    private final List<String> javaArgs;
    private final Map<String, String> properties;
    private final LoggingWebhook loggingWebhook;

    @Nullable
    private ProcessInfo process;

    public DefaultScriptManager(@NonNull List<String> script, @NonNull final List<String> javaArgs, String webhookUrl) {
        this.script = script;
        this.javaArgs = javaArgs;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process != null) {
                process.process().destroy();
            }
        }));

        properties = Map.of();

        if (!webhookUrl.isBlank()) {
            // maybe use a regex?
            webhookUrl = webhookUrl.replace("https://discord.com/api/webhooks/", "");
            loggingWebhook = new LoggingWebhook(webhookUrl.substring(0, webhookUrl.indexOf('/')), webhookUrl.substring(webhookUrl.indexOf('/')));
        } else {
            loggingWebhook = null;
        }
    }

    private void setDiscordActivity(boolean processRunning) {
        final var integ = Main.getDiscordIntegration();
        if (integ != null) {
            integ.setActivity(DiscordIntegration.ActivityType.WATCHING, processRunning ? "a process \uD83D\uDC40" : "nothing \uD83D\uDE22");
        }
    }

    @Override
    public List<String> getScript() {
        return script;
    }

    private Process createProcess() {
        try {
            if (!Files.exists(getAgentPath())) {
                try {
                    Main.copyAgent(this);
                } catch (IOException e) {
                    LOGGER.error("Exception copying agent JAR: ", e);
                    throw new RuntimeException(e);
                }
            }

            LOGGER.info("Starting process...");
            setDiscordActivity(true);
            return new ProcessBuilder(getStartCommand())
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            LOGGER.error("Starting process failed, used start command {}", getStartCommand(), e);
        }
        setDiscordActivity(false); // exception in this case
        return null;
    }

    @Override
    public void tryFirstStart() {
        process = new ProcessInfoImpl(Objects.requireNonNull(createProcess()));
        LOGGER.warn("Started process after launcher start.");
    }

    private List<String> getStartCommand() {
        List<String> command = new ArrayList<>(javaArgs.size() + 2);
        command.add(findJavaBinary());
        final var webhookUrl = loggingWebhook == null ? "" : "/;/" + loggingWebhook.id() + "%%" + loggingWebhook.token();
        command.add("-javaagent:" + getAgentPath().toAbsolutePath() + "=" + Main.RMI_NAME + webhookUrl);
        command.addAll(javaArgs);
        properties.forEach((key, value) -> command.add("-D%s=\"%s\"".formatted(key, value)));
        command.addAll(script);
        return command;
    }

    @Override
    public void startProcess() {
        process = new ProcessInfoImpl(createProcess());
    }

    @Override
    public void clearProcess() {
        process = null;
    }

    @Override
    public @NotNull Path getAgentPath() {
        return Main.AGENT_PATH;
    }

    @Override
    public Optional<String> getProcessVersion() {
        return Optional.empty();
    }

    @Override
    @Nullable
    public ProcessInfo getProcess() {
        return process;
    }

    private class ProcessInfoImpl implements ProcessInfo {
        private final Process process;
        private ProcessConnector connector;

        public ProcessInfoImpl(final Process process) {
            this.process = new DelegatedProcess(process) {
                @Override
                public void destroy() {
                    setDiscordActivity(false);
                    if (connector != null) {
                        try {
                            connector.onShutdown();
                        } catch (RemoteException e) {
                            LOGGER.error("Exception trying to call shutdown listeners: ", e);
                        }
                    }
                    super.destroy();
                }

                @Override
                public Process destroyForcibly() {
                    setDiscordActivity(false);
                    return super.destroyForcibly();
                }
            };
            process.onExit().whenComplete(($, e) -> {
                if (e != null) {
                    DefaultScriptManager.LOGGER.error("Exception exiting process: ", e);
                } else {
                    LOGGER.warn("Process exited successfully.");
                }
            });

            Main.SERVICE.schedule(() -> {
                try {
                    final var registry = LocateRegistry.getRegistry("127.0.0.1", ProcessConnector.PORT);
                    connector = (ProcessConnector) registry.lookup(Main.RMI_NAME);
                    LOGGER.warn("RMI connector has been successfully setup at port {}", ProcessConnector.PORT);
                } catch (Exception e) {
                    LOGGER.error("Exception setting up RMI connector: ", e);
                }
            }, 20, TimeUnit.SECONDS);
        }

        @Override
        public Process process() {
            return process;
        }

        @Override
        @Nullable
        public Release release() {
            return null;
        }

        @Override
        @Nullable
        public ProcessConnector connector() {
            return connector;
        }
    }

    record LoggingWebhook(String id, String token) {
    }

}
