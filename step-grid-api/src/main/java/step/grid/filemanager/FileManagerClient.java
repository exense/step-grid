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
 * Interface for {@link FileManager} clients
 * <p>
 * A {@link FileManagerClient} is responsible for the retrieval and caching of
 * {@link FileVersion} objects on the client side.
 * <p>
 * On the server side, {@link FileVersion} objects are served by a {@link FileManager}
 *
 */
public interface FileManagerClient extends AutoCloseable {

    /**
     * Request the specific version of a file.
     *
     * @param fileVersionId            the version of the File to be retrieved
     * @param cleanableFromClientCache if this version of the file can be cleaned-up from the client cache at runtime. Refer to the cleanup job for details {@link AbstractFileManager#scheduleCleanupJob() }
     * @return the {@link FileVersion} corresponding to the version specified or <code>null</code> if the version isn't available
     * @throws FileManagerException
     */
    FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanableFromClientCache) throws FileManagerException;

    /**
     * Request the specific version of a file, optionally without registering an in-use lock on it.
     * <p>
     * When {@code trackUsage} is {@code false} the version is still downloaded on cache miss and its
     * last access time is refreshed, but the in-use counter is <b>not</b> incremented. This is used by
     * consumers that must not hold a usage lock on the file (e.g. the main agent serving its forked
     * agents, or the grid proxy cache which evicts purely based on TTL). Such callers must not call
     * {@link #releaseFileVersion(FileVersion)} for these requests.
     *
     * @param fileVersionId            the version of the File to be retrieved
     * @param cleanableFromClientCache if this version of the file can be cleaned-up from the client cache at runtime
     * @param trackUsage               whether the in-use counter should be incremented for this request
     * @return the {@link FileVersion} corresponding to the version specified or <code>null</code> if the version isn't available
     * @throws FileManagerException
     */
    default FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanableFromClientCache, boolean trackUsage) throws FileManagerException {
        // Default behavior ignores the trackUsage flag. Caching implementations (e.g. FileManagerClientImpl)
        // override this to skip the in-use lock while still refreshing the last access time.
        return requestFileVersion(fileVersionId, cleanableFromClientCache);
    }

    /**
     * This method should be invoked once a requested file version is not required anymore by the caller
     *
     * @param fileVersion the {@link FileVersion} of the previously registered file.
     */
    void releaseFileVersion(FileVersion fileVersion);

    /**
     * Delete a specific version of a file from the cache
     *
     * @param fileVersionId the version of the File to be removed from the cache
     */
    void removeFileVersionFromCache(FileVersionId fileVersionId);

    /**
     * Removes all cache entries of this cache
     */
    public void cleanupCache();


}
