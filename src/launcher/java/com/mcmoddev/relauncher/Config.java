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

import com.mcmoddev.relauncher.api.LauncherConfig;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@ConfigSerializable
public class Config implements LauncherConfig {
    public static final Config DEFAULT = new Config();

    @Required
    @Setting("mode")
    @Comment("""
        The mode this launcher runs in:
        - JAR => manages a jar, supports updating
        - CUSTOM_SCRIPT => manages a custom java launch script, does NOT support updating""")
    public LauncherMode mode = LauncherMode.JAR;

    @Override
    public LauncherMode getLauncherMode() {
        return mode;
    }

    @Setting("jar_path")
    @Comment("Only if the mode is JAR, the path of the jar to launch.")
    public String jarPath = "file.jar";

    @Required
    @Setting("jvm_args")
    @Comment("The arguments to start the process with.")
    public List<String> jvmArgs = new ArrayList<>();

    @Required
    @Setting("checking_info")
    @Comment("Information about update checking.")
    public CheckerInfo checkingInfo = new CheckerInfo();

    @Required
    @Setting("github")
    @Comment("Information about the github repo for update checking.")
    public GitHub gitHub = new GitHub();

    @Setting("custom_script")
    @Comment("""
        Only if the mode is CUSTOM_SCRIPT, the arguments of the script to run.
        Example for running a Minecraft server: ["@user_jvm_args.txt", "@libraries/net/minecraftforge/forge/1.19-41.0.17/unix_args.txt"]""")
    public List<String> customScript = List.of();

    @ConfigSerializable
    public static final class GitHub {
        @Required
        @Setting("owner")
        @Comment("The owner of the repo to check for updates.")
        public String owner = "";

        @Required
        @Setting("repo")
        @Comment("The name of the repo to check for updates.")
        public String repo = "";
    }

    @ConfigSerializable
    public static final class CheckerInfo {
        @Required
        @Setting("rate")
        @Comment("The rate (in minutes) at which to check for updates. Set to -1 to disable regular checking.")
        public long rate = 10;

        @Required
        @Setting("file_pattern")
        @Comment("A regex that will match the file to use for updating the jar when a release is found.")
        public String filePattern = ".jar";
    }

    @Required
    @Setting("discord")
    @Comment("Configuration for Discord integration.")
    public Discord discord = new Discord();

    @ConfigSerializable
    public static final class Discord {
        @Required
        @Setting("enabled")
        @Comment("If Discord integration should be enabled.")
        public boolean enabled = true;

        @Required
        @Setting("bot_token")
        @Comment("If Discord integration is enabled, the token to use for the bot.")
        public String botToken = "";

        @Required
        @Setting("guild_id")
        @Comment("The ID of the guild in which the bot will work.")
        public String guildId = "";

        @Required
        @Setting("prefixes")
        @Comment("A list of prefixes the bot uses.")
        public List<String> prefixes = List.of("+");

        @Required
        @Setting("roles")
        @Comment("A list of roles which have access to the integration commands.")
        public List<String> roles = List.of();

        @Required
        @Setting("file_pattern")
        @Comment("""
        Regex patterns used to test a file name against in order to see if a file can be accessed through the Discord command.
        An empty list will result in a pattern that will never match a name.
        A '!' in front of a pattern will invert it.
        '*' will make the pattern match any file.
        Patterns are tested in the order they've been defined in: if one of them tests as true, the remaining will not be tested anymore""")
        public List<String> filePatterns = List.of();

        @Required
        @Setting("logging_webhook")
        @Comment("""
        The URL of the webhook to use for discord logging if the launched process has logback on the classpath.
        Can be left empty.""")
        public String loggingWebhook = "";
    }

    @Override
    public CheckingRate getCheckingRate() {
        return new CheckingRate(checkingInfo.rate, TimeUnit.MINUTES);
    }

    @Override
    public boolean isDiscordIntegrationEnabled() {
        return discord.enabled;
    }

    public static <T> T load(final Path path, final Class<T> cfgType, final T defaultValue) throws ConfigurateException {
        final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
            .emitComments(true)
            .prettyPrinting(true)
            .path(path)
            .defaultOptions(opt -> opt.serializers(builder -> builder.register(TypeToken.get(LauncherMode.class), new ModeSerializer())))
            .build();
        final var configSerializer = Objects.requireNonNull(loader.defaultOptions().serializers().get(cfgType));
        final var type = io.leangen.geantyref.TypeToken.get(cfgType).getType();

        if (!Files.exists(path)) {
            try {
                final var node = loader.loadToReference();
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.createFile(path);
                configSerializer.serialize(type, defaultValue, node.node());
                node.save();
            } catch (Exception e) {
                throw new ConfigurateException(e);
            }
        }

        final var configRef = loader.loadToReference();

        { // Add new values to the config
            final var inMemoryNode = CommentedConfigurationNode
                .root(loader.defaultOptions());
            configSerializer.serialize(type, defaultValue, inMemoryNode);
            configRef.node().mergeFrom(inMemoryNode);
            configRef.save();
        }

        return configRef.referenceTo(cfgType).get();
    }

    private static final class ModeSerializer implements TypeSerializer<LauncherMode> {

        @Override
        public LauncherMode deserialize(final Type type, final ConfigurationNode node) throws SerializationException {
            final var str = node.getString();
            if (str == null)
                return null;
            for (final var val : LauncherMode.class.getEnumConstants())
                if (str.equalsIgnoreCase(val.toString()))
                    return val;
            return null;
        }

        @Override
        public void serialize(final Type type, @Nullable final LauncherMode obj, final ConfigurationNode node) throws SerializationException {
            if (obj == null)
                node.raw(null);
            else
                node.set(obj.toString());
        }
    }
}
