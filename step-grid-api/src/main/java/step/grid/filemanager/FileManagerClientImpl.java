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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

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
     * Tracks the downloads currently in progress, keyed by {@link FileVersionId}. This enforces the
     * single-writer guarantee: for any given {@code (fileId, version)} only one thread performs the
     * download while concurrent requests for the same version block on and reuse its result. Downloads of
     * <i>different</i> versions (even of the same file) proceed in parallel.
     */
    private final ConcurrentHashMap<FileVersionId, CompletableFuture<CachedFileVersion>> downloadsInProgress = new ConcurrentHashMap<>();

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
        return requestFileVersion(fileVersionId, cleanableFromClientCache, true);
    }

    @Override
    public FileVersion requestFileVersion(FileVersionId fileVersionId, boolean cleanableFromClientCache, boolean trackUsage) throws FileManagerException {
        // The fileId and version end up as path segments of the cache folder (<cacheRoot>/<fileId>/<version>).
        // They may originate from untrusted HTTP callers (e.g. the main-agent and grid-proxy file endpoints),
        // so reject any value containing path-traversal or separator characters before resolving any path.
        validateFileVersionId(fileVersionId);
        try {
            fileHandleCacheLock.readLock().lock();
            Map<FileVersionId, CachedFileVersion> versionCache = getVersionMap(fileVersionId.getFileId());

            // Fast path: cache hit
            synchronized (versionCache) {
                CachedFileVersion cachedFileVersion = versionCache.get(fileVersionId);
                if (cachedFileVersion != null) {
                    applyUsage(cachedFileVersion, trackUsage);
                    return cachedFileVersion.getFileVersion();
                }
            }

            if (fileProvider == null) {
                return null;
            }

            // Cache miss: ensure a single download per (fileId, version), concurrent callers block and reuse it
            CompletableFuture<CachedFileVersion> ourFuture = new CompletableFuture<>();
            CompletableFuture<CachedFileVersion> inProgress = downloadsInProgress.putIfAbsent(fileVersionId, ourFuture);
            CachedFileVersion cachedFileVersion;
            if (inProgress == null) {
                // We are the single writer for this version (putIfAbsent return null if the entry was absent)
                try {
                    // Re-check the cache: a previous writer may have completed and removed its in-progress entry
                    // between our fast-path miss above and us claiming the download slot. This avoids a redundant
                    // download (and an atomic move onto an already existing final container).
                    synchronized (versionCache) {
                        cachedFileVersion = versionCache.get(fileVersionId);
                    }
                    if (cachedFileVersion == null) {
                        cachedFileVersion = downloadAndStore(fileVersionId, cleanableFromClientCache);
                        synchronized (versionCache) {
                            versionCache.put(fileVersionId, cachedFileVersion);
                        }
                    }
                    ourFuture.complete(cachedFileVersion);
                } catch (Throwable t) {
                    ourFuture.completeExceptionally(t);
                    if (t instanceof FileManagerException) {
                        throw (FileManagerException) t;
                    }
                    throw new FileManagerException(fileVersionId, "Error while downloading file version " + fileVersionId, t);
                } finally {
                    downloadsInProgress.remove(fileVersionId, ourFuture);
                }
            } else {
                // Another thread is already downloading this version, wait for and reuse its result
                cachedFileVersion = joinDownload(fileVersionId, inProgress);
            }
            applyUsage(cachedFileVersion, trackUsage);
            return cachedFileVersion.getFileVersion();
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

    private void applyUsage(CachedFileVersion cachedFileVersion, boolean trackUsage) {
        if (trackUsage) {
            cachedFileVersion.updateUsage();
        } else {
            cachedFileVersion.touch();
        }
    }

    private CachedFileVersion joinDownload(FileVersionId fileVersionId, CompletableFuture<CachedFileVersion> inProgress) throws FileManagerException {
        try {
            return inProgress.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof FileManagerException) {
                throw (FileManagerException) cause;
            }
            throw new FileManagerException(fileVersionId, "Error while waiting for the download of file version " + fileVersionId, cause);
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
