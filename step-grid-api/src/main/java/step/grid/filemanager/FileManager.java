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
import java.io.InputStream;

/**
 * A file-based cache for {@link FileVersion} objects. 
 * 
 * This cache enables the registration, caching and retrieval of different versions of files.
 *
 */
public interface FileManager extends AutoCloseable {

	/**
	 * Cache the content of the file provided as argument under a specific version for later retrieval
	 * The content of the file is persisted under the version returned. Multiple versions of the same file 
	 * can be registered using this method. If the file changed between registrations a new version will be
	 * returned.
	 * 
	 * @param inputStream the {@link InputStream} of the resource to be registered
	 * @param fileName the file name of the resource to be registered
	 * @param isDirectory if the resource is a directory (i.e. is zipped) or not
	 * @param deletePreviousVersions if the previous versions of this file should be deleted
	 * @param cleanable if this version of the file can be cleaned-up at runtime. Refer to the cleanup job for details {@link AbstractFileManager#scheduleCleanupJob() }
	 * @return the {@link FileVersion} of the registered file. The {@link FileVersionId} can be used for later retrival of this version
	 * @throws FileManagerException
	 */
	public FileVersion registerFileVersion(InputStream inputStream, String fileName, boolean isDirectory, boolean deletePreviousVersions, boolean cleanable) throws FileManagerException;

	/**
	 * This method should be invoked once the registered file version is not required anymore by the caller
	 * @param fileVersion the {@link FileVersion} of the previously registered file.
	 */
	public void releaseFileVersion(FileVersion fileVersion);

	/**
	 * Cache the content of the file provided as argument under a specific version for later retrieval
	 * The content of the file is persisted under the version returned. Multiple versions of the same file 
	 * can be registered using this method. If the file changed between registrations a new version will be
	 * returned.
	 * 
	 * @param file the file to be registered
	 * @param deletePreviousVersions if the previous versions of this file should be deleted
	 * @param cleanable if this version of the file can be cleaned-up at runtime. Refer to the cleanup job for details {@link AbstractFileManager#scheduleCleanupJob() }
	 * @return the {@link FileVersion} of the registered file. The {@link FileVersionId} can be used for later retrival of this version
	 * @throws FileManagerException
	 */
	public FileVersion registerFileVersion(File file, boolean deletePreviousVersions, boolean cleanable) throws FileManagerException;
	
	/**
	 * Delete a specific version of a file from the cache
	 * 
	 * @param fileVersionId the version of the File to be removed from the cache
	 */
	void unregisterFileVersion(FileVersionId fileVersionId);
	
	/**
	 * Get the specific version of a file.
	 * 
	 * @param fileVersionId the version of the File to be retrieved
	 * @return the {@link FileVersion} corresponding to the version specified or <code>null</code> if the version isn't available
	 * @throws FileManagerException
	 */
	public FileVersion getFileVersion(FileVersionId fileVersionId) throws FileManagerException;

	/**
	 * Removes all cache entries of this cache
	 */
	public void cleanupCache();


}
