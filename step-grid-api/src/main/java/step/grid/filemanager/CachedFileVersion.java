package step.grid.filemanager;

public class CachedFileVersion {

	private FileVersion fileVersion;

	private boolean cleanable;

	private long lastAccessTime;

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

	public void setLastAccessTime(long lastAccessTime) {
		this.lastAccessTime = lastAccessTime;
	}

	public void updateLastAccessTime() {
		this.lastAccessTime = System.currentTimeMillis();
	}
}
