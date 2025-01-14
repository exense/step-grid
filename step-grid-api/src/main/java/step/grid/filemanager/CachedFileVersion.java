package step.grid.filemanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedFileVersion {

	private static final Logger logger = LoggerFactory.getLogger(CachedFileVersion.class);

	private FileVersion fileVersion;

	private boolean cleanable;

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

	public void setFileVersion(FileVersion fileVersion) {
		this.fileVersion = fileVersion;
	}

	public boolean isCleanable() {
		return cleanable;
	}

	public void setCleanable(boolean cleanable) {
		this.cleanable = cleanable;
	}

	public long getLastAccessTime() {
		return lastAccessTime;
	}

	public int updateUsage() {
		this.lastAccessTime = System.currentTimeMillis();
		int currentCountInUse = this.inUse.incrementAndGet();
		if (logger.isDebugEnabled()) {
			logger.debug("File version {} for file {} updated, new usage count is {}", getFileVersion(), getFileVersion().getFile().getAbsolutePath(), currentCountInUse);
		}
		return currentCountInUse;
	}

	public int releaseUsage() {
		int currentCountInUse = inUse.decrementAndGet();
		if (logger.isDebugEnabled()) {
			logger.debug("File version {} for file {} usage released, new usage count is {}", getFileVersion(), getFileVersion().getFile().getAbsolutePath(), currentCountInUse);
		}
		return currentCountInUse;
	}

	public int getCurrentUsageCount() {
		return inUse.get();
	}

	/**
	 * Update only the last use timestamp
	 */
	public void updateLastUsage() {
		this.lastAccessTime = System.currentTimeMillis();
	}
}
