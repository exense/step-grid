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
package step.grid.contextbuilder;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

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
		return remoteClassLoaderFolder.getFileId()+"_"+remoteClassLoaderFolder.getVersion();
	}

	@Override
	public boolean requiresReload() throws FileManagerException {
		return false;
	}

	private FileVersion requestLatestClassPathFolder() throws FileManagerException {
		return fileManager.requestFileVersion(remoteClassLoaderFolder);			
	}

	@Override
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileManagerException {
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
