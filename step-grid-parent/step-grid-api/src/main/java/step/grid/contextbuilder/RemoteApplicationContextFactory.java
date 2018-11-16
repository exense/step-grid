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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerClient.FileVersion;
import step.grid.filemanager.FileManagerClient.FileVersionId;
import step.grid.filemanager.FileProviderException;

public class RemoteApplicationContextFactory extends ApplicationContextFactory {

	protected FileVersionId remoteClassLoaderFolder;
	
	protected FileManagerClient fileManager;

	public RemoteApplicationContextFactory(FileManagerClient fileManager, FileVersionId remoteClassLoaderFolder) {
		super();
		this.fileManager = fileManager;
		this.remoteClassLoaderFolder = remoteClassLoaderFolder;
	}

	@Override
	public String getId() {
		return remoteClassLoaderFolder.getFileId();
	}

	@Override
	public boolean requiresReload() throws FileProviderException {
		FileVersion localClassLoaderFolder = requestLatestClassPathFolder();
		return localClassLoaderFolder.isModified();
	}

	private FileVersion requestLatestClassPathFolder() throws FileProviderException {
		return fileManager.requestFileVersion(remoteClassLoaderFolder.getFileId(), remoteClassLoaderFolder.getVersion());			
	}

	@Override
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileProviderException {
		FileVersion localClassLoaderFolder = requestLatestClassPathFolder();

		List<URL> urls;
		if (localClassLoaderFolder.getFile().isDirectory()) {
			urls = ClassPathHelper.forAllJarsInFolder(localClassLoaderFolder.getFile());
		} else {
			urls = ClassPathHelper.forSingleFile(localClassLoaderFolder.getFile());
		}	
		
		URL[] urlArray = urls.toArray(new URL[urls.size()]);
		ClassLoader classLoader = new URLClassLoader(urlArray, parentClassLoader);
		return classLoader;
	}

}
