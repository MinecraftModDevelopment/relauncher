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

import java.nio.file.Path;

/**
 * An updater which manages and updates a jar.
 */
public interface JarUpdater extends Runnable, BaseProcessManager {

    /**
     * Kills the currently running process and updates it.
     *
     * @param release the release to update to
     */
    void killAndUpdate(final Release release) throws Exception;

    /**
     * @return the update checker
     */
    UpdateChecker getUpdateChecker();

    /**
     * @return the path of the managed jar
     */
    Path getJarPath();

    /**
     * Runs this updater.
     */
    @Override
    void run();
}
