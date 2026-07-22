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
import java.io.FileWriter;
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
     * The role of this client, which determines for instance how directories are stored. Must match the mode of the
     * {@link FileVersionProvider} (e.g. {@code HttpFileVersionProvider}): a {@link FileManagerClientMode#RELAY}
     * cache (grid proxy, forker main agent) stores directories archived, a {@link FileManagerClientMode#CONSUMER}
     * cache stores them exploded.
     */
    protected final FileManagerClientMode mode;

    /**
     * @param cacheFolder  the folder to be used to store the {@link FileVersion}s
     * @param fileProvider the file provider responsible for the retrieval of the {@link FileVersion} if absent of the cache
     */
    public FileManagerClientImpl(File cacheFolder, FileVersionProvider fileProvider, FileManagerConfiguration fileManagerConfiguration) {
        this(cacheFolder, fileProvider, fileManagerConfiguration, FileManagerClientMode.CONSUMER);
    }

    /**
     * @param mode the role of this client ({@link FileManagerClientMode#CONSUMER} to store directories
     *             exploded, {@link FileManagerClientMode#RELAY} to keep them archived). Must match the mode of
     *             the {@code fileProvider}.
     */
    public FileManagerClientImpl(File cacheFolder, FileVersionProvider fileProvider, FileManagerConfiguration fileManagerConfiguration, FileManagerClientMode mode) {
        super(cacheFolder, fileManagerConfiguration);
        this.fileProvider = fileProvider;
        this.mode = mode;
        reconcileCacheStorageMode();
        loadCache();
    }

    /**
     * Reconciles the on-disk cache with this client's {@link FileManagerClientMode}. The mode is recorded by the
     * presence/absence of the {@link #CACHE_MODE_FILENAME} marker: present means a {@link FileManagerClientMode#RELAY}
     * cache (directories stored archived), absent means a {@link FileManagerClientMode#CONSUMER} cache (directories
     * stored exploded). If the mode changed since the cache was populated, the on-disk layout is incompatible with
     * how files would now be stored and served, so the cache is dropped and rebuilt from the upstream on demand.
     * Runs before {@link #loadCache()}.
     */
    private void reconcileCacheStorageMode() {
        // The mode is persisted by the presence (RELAY) or absence (CONSUMER) of the marker file.
        File marker = new File(cacheFolder, CACHE_MODE_FILENAME);
        boolean relayCacheOnDisk = marker.exists();
        boolean relayMode = mode == FileManagerClientMode.RELAY;
        if (relayCacheOnDisk != relayMode) {
            // The persisted mode differs from the current one: any cached content is stored in a way that is
            // incompatible with how files are now stored and served, so drop the cache and let it rebuild from
            // the upstream on demand. Wiping is a harmless no-op when the cache is empty (e.g. on first start).
            wipeCacheFolder();
            if (relayMode) {
                writeCacheModeMarker(marker);
            }
            logger.info("File manager cache in {} (re)initialized for {} mode", cacheFolder.getAbsolutePath(), mode);
        }
    }

    private void writeCacheModeMarker(File marker) {
        try {
            marker.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(marker)) {
                writer.write(FileManagerClientMode.RELAY.name());
            }
        } catch (IOException e) {
            logger.warn("Could not write the file manager cache mode marker " + marker.getAbsolutePath()
                + ". The cache may be unnecessarily dropped on the next restart.", e);
        }
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
        } catch (IOException e) {
            throw new FileManagerException(fileVersionId, "Error while storing file version " + fileVersionId, e);
        } finally {
            // On success the temp container has been moved into place and no longer exists (no-op); on any
            // failure this discards the partial download so no corrupt/leftover container is left behind.
            FileHelper.safeDeleteFolder(tempContainer);
            // Best-effort removal of the now-idle temp directory so the cache root stays clean. delete() only
            // removes it when empty, so it is a no-op while a concurrent download still holds a container there.
            getTempContainerRootFolder().delete();
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
