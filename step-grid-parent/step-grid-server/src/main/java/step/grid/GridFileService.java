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
package step.grid;

import java.io.File;

import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

public interface GridFileService {

	/**
	 * Register a file into the GRID
	 * 
	 * @param file the file to be registered to the GRID
	 * @return an handle to the registered file. This handle will be used to retrieve the registered file
	 * @throws FileManagerException 
	 */
	FileVersion registerFile(File file) throws FileManagerException;
	
	/**
	 * Get a file that has been previously registered to the GRID
	 * 
	 * @param fileVersionId the handle returned at regitration
	 * @return the registered file
	 * @throws FileManagerException 
	 */
	FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException;

}