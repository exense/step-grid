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
package step.grid;

import java.io.File;

public interface GridFileService {

	/**
	 * Register a file into the GRID
	 * 
	 * @param file the file to be registered to the GRID
	 * @return an handle to the registered file. This handle will be used to retrieve the registered file
	 */
	String registerFile(File file);
	
	/**
	 * Get a file that has been previously registered to the GRID
	 * 
	 * @param fileHandle the handle returned at regitration
	 * @return the registered file
	 */
	File getRegisteredFile(String fileHandle);

}