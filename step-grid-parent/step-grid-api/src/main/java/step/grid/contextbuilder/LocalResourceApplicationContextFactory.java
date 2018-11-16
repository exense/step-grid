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
package step.grid.contextbuilder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import step.grid.bootstrap.ResourceExtractor;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerClient.FileVersion;

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
		List<URL> urls = ClassPathHelper.forSingleFile(jar);
		URL[] urlArray = urls.toArray(new URL[urls.size()]);
		URLClassLoader cl = new URLClassLoader(urlArray, parentClassLoader);
		return cl;	
	}

}
