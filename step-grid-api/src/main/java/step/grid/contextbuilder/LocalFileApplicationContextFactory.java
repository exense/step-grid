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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.filemanager.FileManagerException;

public class LocalFileApplicationContextFactory extends ApplicationContextFactory {

	private static final Logger logger = LoggerFactory.getLogger(LocalFileApplicationContextFactory.class);

	private File jarFile;
	
	public LocalFileApplicationContextFactory(File jarFile) {
		super();
		this.jarFile = jarFile;
	}

	@Override
	public String getId() {
		return jarFile.getAbsolutePath();
	}

	@Override
	public boolean requiresReload() {
		return false;
	}

	@Override
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileManagerException {
		if (logger.isDebugEnabled()) {
			logger.debug("Creating URLClassLoader from local jar file {}", jarFile.getAbsolutePath());
		}
		URL[] urlArray;
		try {
			urlArray = new URL[] {jarFile.toURI().toURL()};
		} catch (MalformedURLException e) {
			throw new FileManagerException(null, e);
		}
		URLClassLoader cl = new URLClassLoader(urlArray, parentClassLoader);
		return cl;	
	}

}
