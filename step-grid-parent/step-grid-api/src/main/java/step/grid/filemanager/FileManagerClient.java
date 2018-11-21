/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.grid.filemanager;

import java.io.File;

public interface FileManagerClient {

	File requestFile(String uid, long lastModified) throws FileProviderException;
	
	FileVersion requestFileVersion(String uid, long lastModified) throws FileProviderException;
	
	String getDataFolderPath();
	
	public static class FileVersionId {
		
		String fileId;
		
		long version;

		public FileVersionId() {
			super();
		}

		public FileVersionId(String fileId, long version) {
			super();
			this.fileId = fileId;
			this.version = version;
		}

		public String getFileId() {
			return fileId;
		}

		public void setFileId(String fileId) {
			this.fileId = fileId;
		}

		public long getVersion() {
			return version;
		}

		public void setVersion(long version) {
			this.version = version;
		}
	}
	
	static class FileVersion {
		
		File file;
		
		boolean modified;
		
		String fileId;
		
		long version;

		public String getFileId() {
			return fileId;
		}

		public void setFileId(String fileId) {
			this.fileId = fileId;
		}

		public File getFile() {
			return file;
		}

		public void setFile(File file) {
			this.file = file;
		}

		public boolean isModified() {
			return modified;
		}

		public void setModified(boolean modified) {
			this.modified = modified;
		}

		public long getVersion() {
			return version;
		}

		public void setVersion(long version) {
			this.version = version;
		}
	}

}