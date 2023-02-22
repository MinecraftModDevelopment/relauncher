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
package com.mcmoddev.relauncher.api;

import java.util.concurrent.TimeUnit;

/**
 * Represents a ReLauncher config.
 */
public interface LauncherConfig {

    /**
     * @return if Discord integration is enabled
     */
    default boolean isDiscordIntegrationEnabled() {
        return true;
    }

    /**
     * Gets the rate at which the update checker will run. <br>
     * Set the amount to {@code -1} in order to disable update checking.
     *
     * @return the rate at which the update checker will run
     */
    default CheckingRate getCheckingRate() {
        return new CheckingRate(-1, TimeUnit.MINUTES);
    }

    /**
     * @return if an exception will be thrown in case the config had just been generated
     */
    default boolean throwIfNew() {
        return true;
    }

    /**
     * @return if the launcher should be able to update itself
     */
    default boolean allowSelfUpdate() {
        return true;
    }

    /**
     * @return gets the mode the launcher will run in
     */
    default LauncherMode getLauncherMode() {
        return LauncherMode.JAR;
    }

    record CheckingRate(long amount, TimeUnit unit) {}

    enum LauncherMode {
        /**
         * The jar mode means that the launcher will manage a one-jar process. <br>
         * This option supports updating.
         */
        JAR,
        /**
         * The custom script mode means that the launcher will manage a custom java script. <br.
         * Useful for Minecraft servers. <br>
         * This option doesn't currently support updating.
         */
        CUSTOM_SCRIPT
    }
}
