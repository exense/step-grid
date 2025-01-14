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

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FileVersion implements AutoCloseable {
	
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

	@Override
	public void close() throws Exception {

	}
}
