package step.grid.filemanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedFileVersion {

    private static final Logger logger = LoggerFactory.getLogger(CachedFileVersion.class);

    private final FileVersion fileVersion;

    private final boolean cleanable;

    private long lastAccessTime;

    private final AtomicInteger inUse = new AtomicInteger(0);

    public CachedFileVersion(FileVersion fileVersion, boolean cleanable) {
        this.fileVersion = fileVersion;
        this.cleanable = cleanable;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public FileVersion getFileVersion() {
        return fileVersion;
    }

    public boolean isCleanable() {
        return cleanable;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Refreshes the last access time of this cached version <b>without</b> incrementing the in-use
     * counter. Used when a file version is read by a consumer that must not register an in-use lock
     * (e.g. the main agent serving a forked agent, or the grid proxy cache). This keeps the TTL based
     * eviction driven by the last actual access while preserving the "no usage tracking" semantics.
     */
    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public int updateUsage() {
        this.lastAccessTime = System.currentTimeMillis();
        int currentCountInUse = this.inUse.incrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("File version {} cache usage increased, new usage count is {}", getFileVersion(), currentCountInUse);
        }
        return currentCountInUse;
    }

    public int releaseUsage() {
        int currentCountInUse = inUse.decrementAndGet();
        if (logger.isDebugEnabled()) {
            logger.debug("File version {} cache usage decreased, new usage count is {}", getFileVersion(), currentCountInUse);
        }
        return currentCountInUse;
    }

    public int getCurrentUsageCount() {
        return inUse.get();
    }
}
