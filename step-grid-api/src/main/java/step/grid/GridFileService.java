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
package step.grid;

import java.io.File;
import java.io.InputStream;

import step.grid.filemanager.AbstractFileManager;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

public interface GridFileService {

	/**
	 * Register a file into the GRID
	 * 
	 * @param file the file to be registered to the GRID
	 * @return an handle to the registered file. This handle will be used to retrieve the registered file
	 * @param cleanable if this version of the file can be cleaned-up at runtime. Refer to the cleanup job for details {@link AbstractFileManager#scheduleCleanupJob() }
	 * @throws FileManagerException 
	 */
	FileVersion registerFile(File file, boolean cleanable) throws FileManagerException;

	/**
	 * This method should be invoked once the registered file version is not required anymore by the caller
	 * @param fileVersion the {@link FileVersion} of the previously registered file.
	 */
	public void releaseFile(FileVersion fileVersion);

	/**
	 * Get a file that has been previously registered to the GRID
	 * 
	 * @param fileVersionId the handle returned at registration
	 * @return the registered file
	 * @throws FileManagerException 
	 */
	FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException;

	
	/**
	 * Unregister a file from the GRID.
	 * 
	 * @param fileVersionId the {@link FileVersionId} of the file
	 */
	void unregisterFile(FileVersionId fileVersionId);

	/**
	 * Register a content into the GRID
	 * 
	 * @param inputStream the {@link InputStream} of the content to be registered
	 * @param fileName the file name of the resource to be registered
	 * @param isDirectory if the resource is a zipped directory or not
	 * @param cleanable if this version of the file can be cleaned-up at runtime
	 * @return an handle to the registered file. This handle will be used to retrieve the registered file
	 * @throws FileManagerException
	 */
	FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory, boolean cleanable) throws FileManagerException;
	
}
