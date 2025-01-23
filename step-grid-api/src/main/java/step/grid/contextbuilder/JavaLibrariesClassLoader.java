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
import java.util.List;
import java.util.Objects;

import ch.exense.commons.io.FileHelper;

public class JavaLibrariesClassLoader extends URLClassLoader {

	public JavaLibrariesClassLoader(File file, ClassLoader parent) throws IOException {
		super(getURLs(file, parent), parent);
	}

	public static URL[] getURLs(File file, ClassLoader parentClassLoader) throws IOException {
		List<URL> urls;
		if (file.isDirectory()) {
			urls = ClassPathHelper.forAllJarsInFolder(file);
		} else {
			if(file.getName().endsWith(".zip")) {
				File tempFolder = unzip(file);
				urls = ClassPathHelper.forAllJarsInFolder(tempFolder);
			} else {
				urls = ClassPathHelper.forSingleFile(file);
			}
		}
		
		URL[] urlArray = urls.toArray(new URL[urls.size()]);
		return urlArray;
	}

	/** Override the default behaviour of parent first lookup of the classloader
	 * When retrieving resources from the JavaLibrariesClassLoader we expect to get it from the provided *child* classloader
	 * and only fall back to the parents if it is not found
	 * <br/>
	 * The main issue otherwise is that the URLClasslodaer.getResourceAsStream would get the URL of the JAR from the parent classloader,
	 * keep a reference to the Zip stream in its closeables map. When the class loader is closed, it closes the "JAR" Zip stream of the parent class loader.
	 * With concurrency and multiple child classloaders referencing the same Jar in the same parent classloader, closing one class loader causes "Stream already closed" exceptions for other threads.
	 * <br/>
	 * This is not really required for agent which has less risk to create a child class loader with a jar that already exists in the bootstrap classloader, but  for the controller and local executions it is required.
	 * @param name The resource name
	 * @return the URL of the found resource or null
	 */
	@Override
	public URL getResource(String name) {
		Objects.requireNonNull(name);
		URL url = findResource(name);
		if (url == null) {
			return super.getResource(name);
		} else {
			return url;
		}
	}

	protected static File unzip(File file) throws IOException {
		File tempFolder = FileHelper.createTempFolder();
		FileHelper.unzip(file, tempFolder);
		FileHelper.deleteFolderOnExit(tempFolder);
		return tempFolder;
	}
}
