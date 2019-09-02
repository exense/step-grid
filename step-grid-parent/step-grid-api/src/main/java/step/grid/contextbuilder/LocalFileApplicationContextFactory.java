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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import step.grid.filemanager.FileManagerException;

public class LocalFileApplicationContextFactory extends ApplicationContextFactory {

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
