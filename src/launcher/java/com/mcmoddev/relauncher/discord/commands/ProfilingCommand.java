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
package com.mcmoddev.relauncher.discord.commands;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.mcmoddev.relauncher.Config;
import com.mcmoddev.relauncher.Constants;
import com.mcmoddev.relauncher.Main;
import com.mcmoddev.relauncher.api.BaseProcessManager;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.AttachedFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public class ProfilingCommand extends RLCommand {
    public static final Path DIRECTORY_PATH = Main.RELAUNCHER_DIR.resolve("profiling");

    public ProfilingCommand(final Supplier<BaseProcessManager> jarUpdater, final Config.Discord config) {
        super(jarUpdater, config);
        name = "profiling";
        help = "Profiling related commands.";
        options = List.of(
                new OptionData(OptionType.STRING, "type", "The type of the profiler to run.")
                        .addChoice("Process", "process")
        );
    }

    @Override
    protected void exec(final SlashCommandEvent event) {
        final var updater = processManager.get();
        final var process = updater.getProcess();
        final var connector = process == null ? null : process.connector();
        if (connector == null) {
            event.deferReply(true).setContent("The process is not running or the agent wasn't attached!").queue();
            return;
        }
        event.deferReply()
                .flatMap(hook -> {
                    try {
                        if (!Files.exists(DIRECTORY_PATH)) {
                            Files.createDirectories(DIRECTORY_PATH);
                        }
                        return switch (event.getOption("type", "", OptionMapping::getAsString)) {
                            case "process" -> {
                                final var result = connector.getProcessInfoProfiling();
                                final var file = DIRECTORY_PATH.resolve(Instant.now().getEpochSecond() + ".json");
                                try (final var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE)) {
                                    Constants.GSON.toJson(result, writer);
                                    yield hook.editOriginalAttachments(AttachedFile.fromData(file.toFile(), "profiling.json"));
                                }
                            }
                            default -> hook.editOriginal("Invalid type provided!");
                        };
                    } catch (Exception e) {
                        Main.LOG.error("Exception getting profiling results: ", e);
                        return hook.editOriginal("There was an exception getting profiling results: " + e.getLocalizedMessage());
                    }
                })
                .queue();
    }
}
