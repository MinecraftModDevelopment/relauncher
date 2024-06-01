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
package com.mcmoddev.relauncher.api;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface UpdateChecker {

    /**
     * Resolves the latest release.
     *
     * @return the latest release
     */
    @Nullable
    Release resolveLatestRelease() throws IOException, InterruptedException;

    /**
     * Finds a new release.
     *
     * @return if a new release has been found
     */
    boolean findNew() throws IOException, InterruptedException;

    /**
     * Gets the latest found release. This might not always be up-to-date.
     *
     * @return the latest found release
     */
    @Nullable
    Release getLatestFound();

    /**
     * Gets a release by a tag name.
     *
     * @param tagName the tag name of the release to get
     * @return the release, if found or else null
     */
    @Nullable
    Release getReleaseByTagName(String tagName) throws IOException, InterruptedException;
}
