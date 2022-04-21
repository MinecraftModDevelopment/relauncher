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
package com.mcmoddev.relauncher.api;

public interface DiscordIntegration {

    /**
     * Sets the activity of the Discord bot managed by the integration.
     *
     * @param type the type of the activity
     * @param name the name of the activity
     */
    void setActivity(ActivityType type, String name);

    /**
     * Shuts down the integration.
     */
    default void shutdown() {

    }

    enum ActivityType {
        PLAYING,
        STREAMING,
        LISTENING,
        WATCHING,
        CUSTOM_STATUS,
        COMPETING
    }
}
