/*
 * ReLauncher - https://github.com/MinecraftModDevelopment/ReLauncher
 * Copyright (C) 2016-2023 <MMD - MinecraftModDevelopment>
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
import com.mcmoddev.relauncher.Main;
import com.mcmoddev.relauncher.api.BaseProcessManager;
import com.mcmoddev.relauncher.api.JarUpdater;
import com.mcmoddev.relauncher.api.Release;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class UpdateCommand extends RLCommand {
    public UpdateCommand(final Supplier<BaseProcessManager> jarUpdater, final Config.Discord config) {
        super(jarUpdater, config);
        name = "update";
        help = "Updates the process' jar.";
        options = List.of(
            new OptionData(OptionType.STRING, "tag", "The tag to which to update. Don't provide to update to latest."),
            new OptionData(OptionType.BOOLEAN, "self-update", "If the launcher should self-update to the specified tag.")
        );
    }

    @Override
    protected void exec(final SlashCommandEvent event) {
        final var manager = this.processManager.get();
        final var selfUpdate = event.getOption("self-update", false, OptionMapping::getAsBoolean);
        final var tagOption = event.getOption("tag");
        if (selfUpdate && tagOption == null) {
            event.reply("Please specify a tag to self update to!").queue();
            return;
        }
        if (selfUpdate) {
            selfUpdate(tagOption.getAsString(), event);
            return;
        }
        if (!(manager instanceof JarUpdater jarUpdater)) {
            event.deferReply(true).setContent("The launcher is running in a mode that doesn't support updating!");
            return;
        }
        if (tagOption == null) {
            event.reply("Trying to update to latest...")
                .map(hook -> {
                    final Release latest;
                    try {
                        latest = jarUpdater.getUpdateChecker().resolveLatestRelease();
                    } catch (IOException | InterruptedException e) {
                        return hook.editOriginal("Could not find latest release.");
                    }
                    return Optional.ofNullable(latest)
                        .map(rel -> hook.editOriginal("Found release \"%s\". Updating...".formatted(latest.name()))
                        .flatMap(msg -> {
                            try {
                                jarUpdater.killAndUpdate(latest);
                                return msg.editMessage("Successfully updated to release \"%s\"!".formatted(latest.name()));
                            } catch (Exception e) {
                                return msg.editMessage("Exception while trying to update the old jar: " + e.getLocalizedMessage());
                            }
                        })).orElseGet(() -> hook.editOriginal("Cannot update to latest release as I can't find a matching asset in it."));
                })
                .queue(RestAction::queue);
        } else {
            final var tag = tagOption.getAsString();
            event.deferReply()
                .setContent("Trying to update to tag: " + tag)
                .map(hook -> {
                    try {
                        final var release = jarUpdater.getUpdateChecker().getReleaseByTagName(tag);
                        return Optional.ofNullable(release)
                            .map(asset -> hook.editOriginal("Found release \"%s\". Updating...".formatted(release.name()))
                            .flatMap(msg -> {
                                try {
                                    jarUpdater.killAndUpdate(release);
                                    return msg.editMessage("Successfully updated to release \"%s\"!".formatted(release.name()));
                                } catch (Exception e) {
                                    return msg.editMessage("Exception while trying to update the old jar: " + e.getLocalizedMessage());
                                }
                            })).orElseGet(() -> hook.editOriginal("Cannot update to the specified tag as I can't find a matching asset in it, or it doesn't exist."));
                    } catch (Exception e) {
                        return hook.editOriginal("Exception trying to find tag: " + e.getLocalizedMessage());
                    }
                })
                .queue(RestAction::queue);
        }
    }

    private void selfUpdate(String tagName, SlashCommandEvent event) {
        event.deferReply()
            .setContent("Updating...")
            .queue(hook -> {
                try {
                    Main.LOG.error("Self-updating to tag '{}' at the request of {}({}) via Discord.", tagName, event.getUser().getAsTag(), event.getUser().getId());
                    Main.selfUpdate(tagName);
                } catch (Exception e) {
                    Main.LOG.error("Exception trying to self-update to tag '{}': {}", tagName, e);
                    hook.editOriginal("Exception trying to self-update to tag '%s': %s".formatted(tagName, e)).queue();
                }
            });
    }

}
