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
package com.mcmoddev.relauncher.discord;

import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.mcmoddev.relauncher.Config;
import com.mcmoddev.relauncher.Main;
import com.mcmoddev.relauncher.api.BaseProcessManager;
import com.mcmoddev.relauncher.api.DiscordIntegration;
import com.mcmoddev.relauncher.discord.commands.ProfilingCommand;
import com.mcmoddev.relauncher.discord.commands.ShutdownCommand;
import com.mcmoddev.relauncher.discord.commands.StartCommand;
import com.mcmoddev.relauncher.discord.commands.StatusCommand;
import com.mcmoddev.relauncher.discord.commands.UpdateCommand;
import com.mcmoddev.relauncher.discord.commands.file.FileCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class DefaultDiscordIntegration implements DiscordIntegration {
    private final JDA jda;

    public DefaultDiscordIntegration(final Path basePath, final Config.Discord config, final Supplier<BaseProcessManager> updater) {
        final var statusCmd = new StatusCommand(updater, config);
        final var commandClient = new CommandClientBuilder()
                .setOwnerId("0000000000")
                .setActivity(null)
                .forceGuildOnly(config.guildId)
                .setPrefixes(config.prefixes.toArray(String[]::new))
                .addSlashCommands(
                        new UpdateCommand(updater, config),
                        new ShutdownCommand(updater, config),
                        new StartCommand(updater, config),
                        statusCmd,
                        new FileCommand(basePath, config),
                        new ProfilingCommand(updater, config)
                )
                .build();

        try {
            jda = JDABuilder.createLight(config.botToken)
                    .setStatus(OnlineStatus.IDLE)
                    .addEventListeners(commandClient, statusCmd)
                    .setRateLimitScheduler(Executors.newScheduledThreadPool(1, r -> {
                        final var t = new Thread(Main.THREAD_GROUP, r, "RLDiscordRateLimiter");
                        t.setDaemon(true);
                        return t;
                    }), true)
                    .setGatewayPool(Main.SERVICE)
                    .build()
                    .setRequiredScopes("applications.commands", "bot");
        } catch (InvalidTokenException e) {
            throw new RuntimeException("Please provide a valid bot token!");
        }

    }

    @Override
    public void setActivity(final ActivityType type, final String name) {
        jda.getPresence().setActivity(Activity.of(Activity.ActivityType.valueOf(type.name()), name));
    }

    @Override
    public void shutdown() {
        jda.shutdown();
    }
}
