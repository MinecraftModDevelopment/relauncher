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
package com.mcmoddev.relauncher.discord.commands.file;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.mcmoddev.relauncher.Config;
import com.mcmoddev.relauncher.Main;
import com.mcmoddev.relauncher.discord.commands.RLCommand;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileCommand extends RLCommand {
    private final Path basePath;
    private final List<Pred> predicates;

    public FileCommand(final Path basePath, final Config.Discord config) {
        super(() -> null, config);
        this.basePath = basePath;
        name = "file";
        help = "File management commands.";

        if (config.filePatterns.isEmpty()) {
            predicates = List.of(new Pred(s -> false, true));
        } else {
            predicates = config.filePatterns.stream()
                .map(str -> {
                    if (str.equals("*")) {
                        return new Pred(s -> true, false);
                    } else if (str.startsWith("!") && str.length() > 1) {
                        final var regex = Pattern.compile(str.substring(1)).asPredicate();
                        return new Pred(s -> !regex.test(s), true);
                    } else {
                        final var regex = Pattern.compile(str);
                        return new Pred(regex.asPredicate(), false);
                    }
                })
                .toList();
        }

        this.children = new SlashCommand[]{
            new Cmd(this::onFileGet) {

                @Override
                public void init() {
                    name = "get";
                    help = "Gets a file.";
                    options = List.of(
                        new OptionData(OptionType.STRING, "path", "The path of the file to get.", true),
                        new OptionData(OptionType.BOOLEAN, "zip", "If true, the provided directory at the path, will be zipped and sent.")
                    );
                }
            },

            new Cmd(this::onFileUpload) {

                @Override
                public void init() {
                    name = "upload";
                    help = "Uploads a file.";
                    options = List.of(
                        new OptionData(OptionType.STRING, "path", "The path to upload the file to.", true),
                        new OptionData(OptionType.ATTACHMENT, "file", "The file to upload. Mutually exclusive with 'url'"),
                        new OptionData(OptionType.STRING, "url", "An url to download the file from, if it is too big. Mutually exclusive with 'file'")
                    );
                }
            }
        };
    }

    protected void onFileUpload(final SlashCommandEvent event) {
        if (event.getMember() == null || event.getMember().getRoles().stream().noneMatch(role -> roles.contains(role.getId()))) {
            event.deferReply(true).setContent("You do not have the required permissions to run this command.").queue();
            return;
        }
        final var file = basePath.resolve(event.getOption("path", "", OptionMapping::getAsString));
        if (!canAccessFile(file)) {
            event.deferReply(true).setContent("You do not have access to the specified path.").queue();
            return;
        }
        if (Files.isDirectory(file)) {
            event.deferReply(true).setContent("The specified path is a directory.").queue();
            return;
        }
        final var attach = event.getOption("file", OptionMapping::getAsAttachment);
        final var url = event.getOption("url", OptionMapping::getAsString);
        if (attach == null && url == null) {
            event.deferReply(true).setContent("Please provide either the file to upload or a link to it.");
            return;
        }
        event.deferReply()
            .queue(hook -> {
                if (attach != null) {
                    attach.getProxy().downloadToFile(file.toFile())
                        .whenComplete(($, e) -> {
                            if (e == null) {
                                hook.editOriginal("File downloaded successfully.").queue();
                                Main.LOG.info("Downloaded file {} from Discord at the request of {}.", $, event.getUser().getAsTag());
                            } else {
                                hook.editOriginal("The downloaded encountered an error: " + e.getLocalizedMessage()).queue();
                                Main.LOG.error("Error downloading file {} from discord at the request of {}: {}", file, event.getUser().getAsTag(), e);
                            }
                        });
                } else {
                    try {
                        Objects.requireNonNull(url);
                        Files.deleteIfExists(file);
                        final var parent = file.toAbsolutePath().getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        try (final var readChannel = Channels.newChannel(new URL(url).openStream())) {
                            Files.createFile(file);
                            try (final var writeChannel = new FileOutputStream(file.toFile()).getChannel()) {
                                writeChannel.transferFrom(readChannel, 0, Long.MAX_VALUE);
                                hook.editOriginal("Successfully downloaded file.").queue();
                                Main.LOG.info("Downloaded file {} from {} at the request of {} via Discord.", file, url, event.getUser().getAsTag());
                            }
                        }
                    } catch (IOException e) {
                        hook.editOriginal("The downloaded encountered an error: " + e.getLocalizedMessage()).queue();
                        Main.LOG.info("Error downloading file {} from {} at the request of {} via Discord: {}", file, url, event.getUser().getAsTag(), e);
                    }
                }
            });
    }

    protected void onFileGet(final SlashCommandEvent event) {
        if (event.getMember() == null || event.getMember().getRoles().stream().noneMatch(role -> roles.contains(role.getId()))) {
            event.deferReply(true).setContent("You do not have the required permissions to run this command.").queue();
            return;
        }
        final var doZip = event.getOption("zip", false, OptionMapping::getAsBoolean);
        if (doZip) {
            final var dir = basePath.resolve(event.getOption("path", "", OptionMapping::getAsString));
            event.deferReply()
                .flatMap(hook -> {
                    try {
                        final var data = filesZip(dir);
                        return hook.editOriginal(data, "dir.zip");
                    } catch (IOException e) {
                        Main.LOG.error("Exception trying to zip directory '{}': ", dir, e);
                        return hook.editOriginal("Exception trying to zip directory: " + e.getLocalizedMessage());
                    }
                })
                .queue();
            return;
        }

        final var file = basePath.resolve(event.getOption("path", "", OptionMapping::getAsString));
        if (!canAccessFile(file)) {
            event.deferReply(true).setContent("You do not have access to the specified path.").queue();
            return;
        }
        if (Files.isDirectory(file)) {
            event.deferReply(true).setContent("The specified path is a directory.").queue();
            return;
        }
        if (!Files.exists(file)) {
            event.deferReply(true).setContent("The specified file doesn't exist.").queue();
            return;
        }
        event.deferReply()
            .flatMap(hook -> hook.editOriginal(file.toFile()))
            .queue();
    }

    public boolean canAccessFile(final Path file) {
        final var ts = file.toAbsolutePath().toString();
        if (file.startsWith(Main.RELAUNCHER_DIR)) return false;
        for (final var pred : predicates) {
            final var res = pred.test(ts);
            if (!pred.ignore(res)) return res;
        }
        return true;
    }

    public byte[] filesZip(Path directory) throws IOException {
        final var bo = new ByteArrayOutputStream();
        final var zipOut = new ZipOutputStream(bo);
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                try {
                    if (canAccessFile(file.toAbsolutePath())) {
                        final var zipEntry = new ZipEntry(file.toString());
                        zipOut.putNextEntry(zipEntry);
                        zipOut.write(Files.readAllBytes(file));
                        zipOut.closeEntry();
                    }
                } catch (IOException e) {
                    sneakyThrow(e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        zipOut.close();
        return bo.toByteArray();
    }

    /**
     * Sneakily throws the given exception, bypassing compile-time checks for
     * checked exceptions.
     *
     * <p>
     * <strong>This method will never return normally.</strong> The exception passed
     * to the method is always rethrown.
     * </p>
     *
     * @param ex the exception to sneakily rethrow
     */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable ex) throws E {
        throw (E) ex;
    }

    private static abstract class Cmd extends SlashCommand {
        private final Consumer<? super SlashCommandEvent> onEvent;

        public Cmd(final Consumer<? super SlashCommandEvent> onEvent) {
            this.onEvent = onEvent;
            init();
        }

        @Override
        protected void execute(final SlashCommandEvent event) {
            onEvent.accept(event);
        }

        public abstract void init();
    }

    public static final class Pred implements Predicate<String> {
        private final Predicate<? super String> predicate;
        private final boolean ignoreIf;

        public Pred(final Predicate<? super String> predicate, final boolean ignoreIf) {
            this.predicate = predicate;
            this.ignoreIf = ignoreIf;
        }

        @Override
        public boolean test(final String s) {
            return predicate.test(s);
        }

        public boolean ignore(final boolean bool) {
            return bool == ignoreIf;
        }
    }
}
