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
package step.grid.contextbuilder;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.filemanager.FileManagerException;

/**
 * This {@link ApplicationContextFactory} builds a classloader based on a folder
 * of jars. The resulting classloader will load all the jars contained in the specified
 * folder
 *
 */
public class LocalFolderApplicationContextFactory extends ApplicationContextFactory {

	private static final Logger logger = LoggerFactory.getLogger(LocalFolderApplicationContextFactory.class);

	protected File libFolder;

	public LocalFolderApplicationContextFactory(File libFolder) {
		super();
		this.libFolder = libFolder;
	}

	@Override
	public String getId() {
		return libFolder.getAbsolutePath();
	}

	@Override
	public boolean requiresReload() {
		return false;
	}

	@Override
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileManagerException {
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Creating JavaLibrariesClassLoader for folder {}", libFolder.getAbsolutePath());
			}
			return new JavaLibrariesClassLoader(libFolder, parentClassLoader);
		} catch (IOException e) {
			throw new FileManagerException(null, e);
		}
	}

}
