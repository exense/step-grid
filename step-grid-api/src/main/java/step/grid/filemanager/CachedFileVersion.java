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
