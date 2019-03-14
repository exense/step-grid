package step.grid.filemanager;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FileVersion {
	
	protected File file;
	
	protected FileVersionId versionId;

	protected boolean directory;
	
	public FileVersion() {
		this(null, null, false);
	}

	public FileVersion(File file, FileVersionId versionId, boolean directory) {
		super();
		this.file = file;
		this.versionId = versionId;
		this.directory = directory;
	}

	public FileVersionId getVersionId() {
		return versionId;
	}

	@JsonIgnore
	public String getFileId() {
		return versionId.getFileId();
	}

	public File getFile() {
		return file;
	}

	@JsonIgnore
	public String getVersion() {
		return versionId.getVersion();
	}

	public boolean isDirectory() {
		return directory;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public void setVersionId(FileVersionId versionId) {
		this.versionId = versionId;
	}

	public void setDirectory(boolean directory) {
		this.directory = directory;
	}

	@Override
	public String toString() {
		return "FileVersion [file=" + file + ", versionId=" + versionId + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
		result = prime * result + ((versionId == null) ? 0 : versionId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileVersion other = (FileVersion) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		if (versionId == null) {
			if (other.versionId != null)
				return false;
		} else if (!versionId.equals(other.versionId))
			return false;
		return true;
	}
}