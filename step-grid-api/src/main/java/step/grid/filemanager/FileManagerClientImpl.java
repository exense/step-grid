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

import java.io.File;
import java.io.IOException;
import java.util.Map;

import ch.exense.commons.io.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link FileManagerClient} which delegates the retrieval of {@link FileVersion} to
 * a {@link FileVersionProvider}
 *
 */
public class FileManagerClientImpl extends AbstractFileManager implements FileManagerClient {

    private static final Logger logger = LoggerFactory.getLogger(FileManagerClientImpl.class);

    protected FileVersionProvider fileProvider;

    /**
     * @param cacheFolder  the folder to be used to store the {@link FileVersion}s
     * @param fileProvider the file provider responsible for the retrieval of the {@link FileVersion} if absent of the cache
     */
    public FileManagerClientImpl(File cacheFolder, FileVersionProvider fileProvider, FileManagerConfiguration fileManagerConfiguration) {
        super(cacheFolder, fileManagerConfiguration);
        this.fileProvider = fileProvider;
        loadCache();
    }

    @Override
    public FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanableFromClientCache) throws FileManagerException {
        // The fileId and version end up as path segments of the cache folder (<cacheRoot>/<fileId>/<version>).
        // They may originate from untrusted HTTP callers (e.g. the main-agent and grid-proxy file endpoints),
        // so reject any value containing path-traversal or separator characters before resolving any path.
        validateFileVersionId(fileVersionId);
        try {
            fileHandleCacheLock.readLock().lock();
            Map<FileVersionId, CachedFileVersion> versionCache = getVersionMap(fileVersionId.getFileId());
            // The whole lookup / download / publication / usage sequence is serialized on the version map monitor,
            // the same monitor the release and eviction paths synchronize on. This guarantees a single download
            // per (fileId, version) and, crucially when the cache is configured for immediate eviction (a TTL of
            // 0, where a version is deleted as soon as its usage drops back to 0 in releaseFileVersionFromCache),
            // that a version can never be evicted between being published to the cache and the caller acquiring
            // its usage lock.
            synchronized (versionCache) {
                CachedFileVersion cachedFileVersion = versionCache.get(fileVersionId);
                if (cachedFileVersion == null) {
                    if (fileProvider == null) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Cache miss: file version " + fileVersionId + " not in local cache and no file provider "
                                + "configured to fetch it from an upstream, returning null");
                        }
                        return null;
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Cache miss: file version " + fileVersionId + " not in local cache, fetching it from the upstream");
                    }
                    long downloadStart = System.currentTimeMillis();
                    cachedFileVersion = downloadAndStore(fileVersionId, cleanableFromClientCache);
                    versionCache.put(fileVersionId, cachedFileVersion);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Fetched file version " + fileVersionId + " from the upstream and stored it in the local cache in "
                            + (System.currentTimeMillis() - downloadStart) + "ms");
                    }
                } else if (logger.isDebugEnabled()) {
                    logger.debug("Cache hit: file version " + fileVersionId + " served from local cache");
                }
                cachedFileVersion.updateUsage();
                return cachedFileVersion.getFileVersion();
            }
        } finally {
            fileHandleCacheLock.readLock().unlock();
        }
    }

    /**
     * Guards against path traversal: the {@code fileId} and {@code version} are used as single path segments
     * of the cache folder, so neither may be {@code null} nor contain {@code ".."} or a path separator.
     */
    private static void validateFileVersionId(FileVersionId fileVersionId) {
        if (fileVersionId == null) {
            throw new IllegalArgumentException("fileVersionId must not be null");
        }
        validatePathSegment("fileId", fileVersionId.getFileId());
        validatePathSegment("version", fileVersionId.getVersion());
    }

    private static void validatePathSegment(String name, String value) {
        if (value == null || value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException("Invalid " + name + ": must not be null or contain path traversal sequences");
        }
    }

    /**
     * Downloads the file version into a temporary container and atomically moves it to its final location.
     * This guarantees that a partially downloaded version is never exposed and that an interrupted download
     * (e.g. a crash) leaves no usable but corrupt entry behind.
     */
    private CachedFileVersion downloadAndStore(FileVersionId fileVersionId, boolean cleanable) throws FileManagerException {
        File tempContainer = createTempContainerFolder(fileVersionId);
        try {
            FileVersion downloaded = fileProvider.saveFileVersionTo(fileVersionId, tempContainer);
            CachedFileVersion tempCachedFileVersion = new CachedFileVersion(downloaded, cleanable);
            createMetaFileInContainer(tempContainer, null, tempCachedFileVersion);

            File finalContainer = getFinalContainerFolder(fileVersionId);
            moveContainerIntoPlace(tempContainer, finalContainer);

            // The data file moved together with its container, rebuild the FileVersion pointing to the final location
            File finalDataFile = new File(finalContainer, downloaded.getFile().getName());
            FileVersion finalFileVersion = new FileVersion(finalDataFile, fileVersionId, downloaded.isDirectory());
            return new CachedFileVersion(finalFileVersion, cleanable);
        } catch (FileManagerException e) {
            FileHelper.safeDeleteFolder(tempContainer);
            throw e;
        } catch (IOException e) {
            FileHelper.safeDeleteFolder(tempContainer);
            throw new FileManagerException(fileVersionId, "Error while storing file version " + fileVersionId, e);
        } catch (RuntimeException e) {
            FileHelper.safeDeleteFolder(tempContainer);
            throw e;
        }
    }

    @Override
    public void releaseFileVersion(FileVersion fileVersion) {
        releaseFileVersionFromCache(fileVersion);
    }

    @Override
    public void removeFileVersionFromCache(FileVersionId fileVersionId) {
        removeFileVersion(fileVersionId);
    }
}
