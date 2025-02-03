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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.bootstrap.ResourceExtractor;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileVersion;

public class LocalResourceApplicationContextFactory extends ApplicationContextFactory {

	private static final Logger logger = LoggerFactory.getLogger(LocalResourceApplicationContextFactory.class);

	String resourceName;
	
	ClassLoader resourceClassLoader;
	
	protected FileManagerClient fileManager;
	
	FileVersion localClassLoaderFolder;
	private File jar;

	public LocalResourceApplicationContextFactory(ClassLoader resourceClassLoader, String resourceName) {
		super();
		this.resourceName = resourceName;
		this.resourceClassLoader = resourceClassLoader;
	}

	@Override
	public String getId() {
		return resourceName;
	}

	@Override
	public boolean requiresReload() {
		return false;
	}

	@Override
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) {
		jar = ResourceExtractor.extractResource(resourceClassLoader, resourceName);
		if (logger.isDebugEnabled()) {
			logger.debug("Creating URLClassLoader from extracted local resource file {}", jar.getAbsolutePath());
		}
		jar.deleteOnExit();
		List<URL> urls = ClassPathHelper.forSingleFile(jar);
		URL[] urlArray = urls.toArray(new URL[urls.size()]);
		URLClassLoader cl = new URLClassLoader(urlArray, parentClassLoader);
		return cl;	
	}

	@Override
	public void onClassLoaderClosed() {
		if (jar != null) {
			try {
				if (logger.isDebugEnabled()) {
					logger.debug("Deleting extracted jar file {}.", jar);
				}
				Files.deleteIfExists(jar.toPath());
			} catch (IOException e) {
				logger.error("Unable to delete the extracted JAR file.", e);
			}
		}
	}

}
