/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
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