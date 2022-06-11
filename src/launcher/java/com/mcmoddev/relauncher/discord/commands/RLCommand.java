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
package com.mcmoddev.relauncher.discord.commands;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.mcmoddev.relauncher.Config;
import com.mcmoddev.relauncher.api.BaseProcessManager;

import java.util.List;
import java.util.function.Supplier;

public abstract class RLCommand extends SlashCommand {

    protected final Supplier<BaseProcessManager> processManager;
    protected final List<String> roles;

    protected RLCommand(final Supplier<BaseProcessManager> processManager, final Config.Discord config) {
        this.processManager = processManager;
        this.roles = config.roles;
        guildOnly = true;
    }

    @Override
    protected final void execute(final SlashCommandEvent event) {
        if (event.getMember() != null || event.getMember().getRoles().stream().noneMatch(role -> roles.contains(role.getId()))) {
            event.deferReply(true).setContent("You do not have the required permissions to run this command.").queue();
            return;
        }
        exec(event);
    }

    protected void exec(final SlashCommandEvent event) {

    }
}
