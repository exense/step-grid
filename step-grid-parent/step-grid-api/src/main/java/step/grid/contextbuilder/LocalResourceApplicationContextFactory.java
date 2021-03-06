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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import step.grid.bootstrap.ResourceExtractor;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileVersion;

public class LocalResourceApplicationContextFactory extends ApplicationContextFactory {

	String resourceName;
	
	ClassLoader resourceClassLoader;
	
	protected FileManagerClient fileManager;
	
	FileVersion localClassLoaderFolder;
	
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
		File jar = ResourceExtractor.extractResource(resourceClassLoader, resourceName);
		jar.deleteOnExit();
		List<URL> urls = ClassPathHelper.forSingleFile(jar);
		URL[] urlArray = urls.toArray(new URL[urls.size()]);
		URLClassLoader cl = new URLClassLoader(urlArray, parentClassLoader);
		return cl;	
	}

}
