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
import com.mcmoddev.relauncher.DefaultJarUpdater;
import com.mcmoddev.relauncher.api.BaseProcessManager;

import java.util.function.Supplier;

public class ShutdownCommand extends RLCommand {
    public ShutdownCommand(final Supplier<BaseProcessManager> jarUpdater, final Config.Discord config) {
        super(jarUpdater, config);
        name = "shutdown";
        help = "Shuts down the process.";
    }

    @Override
    protected void exec(final SlashCommandEvent event) {
        final var updater = processManager.get();
        final var process = updater.getProcess();
        if (process == null) {
            event.deferReply().setContent("No process is running! Use `/start` to start it.").queue();
            return;
        }
        event.deferReply()
                .setContent("Shutting down the process!")
                .queue(hook -> {
                    DefaultJarUpdater.LOGGER.warn("Destroying process at the request of {} via Discord.", event.getUser().getName());
                    process.process().onExit().whenComplete(($, $$) -> {
                        if ($$ != null) {
                            hook.editOriginal("Exception destroying process: " + $$.getLocalizedMessage()).queue();
                        }
                        hook.editOriginal("Successfully destroyed process!").queue();
                    });
                    process.process().destroy();
                    updater.clearProcess();
                });
    }
}
