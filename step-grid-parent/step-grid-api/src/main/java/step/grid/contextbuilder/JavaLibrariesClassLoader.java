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

	protected static File unzip(File file) throws IOException {
		File tempFolder = FileHelper.createTempFolder();
		FileHelper.unzip(file, tempFolder);
		FileHelper.deleteFolderOnExit(tempFolder);
		return tempFolder;
	}
}
