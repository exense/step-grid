/*******************************************************************************
 * Copyright (C) 2020, exense GmbH
 *
 * This file is part of STEP
 *
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package step.grid.filemanager;

/**
 * The role a {@link FileManagerClient} plays with respect to the files it caches, which determines how
 * directories are stored on disk.
 */
public enum FileManagerClientMode {

    /**
     * The client is the final consumer of the files (e.g. an executing agent). Directories are
     * stored <b>exploded</b> (unzipped) so they can be used directly, for instance to build a classpath.
     */
    CONSUMER,

    /**
     * The client does not consume the files itself, it only re-serves them to downstream consumers (the
     * grid proxy, or a main agent serving its forked agents). Directories are kept <b>archived</b> (as a
     * single zip) and streamed back verbatim, avoiding a pointless unzip-on-download and zip-on-serve round-trip.
     */
    RELAY
}
