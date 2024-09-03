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
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ClassPathHelper {

	public static List<URL> forSingleFile(File file) {
		List<URL> urls = new ArrayList<>();
		try {
			addFileToUrls(urls, file);
		} catch (IOException e) {
			throw new RuntimeException("Error getting url list for file "+file.getAbsolutePath());
		}
		return urls;
	}
	
	public static List<URL> forAllJarsInFolderUsingFilter(File folder, FilenameFilter addtitionalFilter) {
		List<URL> urls = new ArrayList<>();
		
		try {
			addFilesToUrls(urls, folder, new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isDirectory()||(pathname.getName().endsWith(".jar")&&(addtitionalFilter==null||addtitionalFilter.accept(folder, pathname.getName())));
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Error getting url list for directory "+folder.getAbsolutePath());
		}
		return urls;
	}
	
	public static List<URL> forAllJarsInFolder(File folder) {
		return forAllJarsInFolderUsingFilter(folder, null);
	}
	
	public static List<URL> forClassPathString(String classPathString) throws MalformedURLException, IOException {
		List<URL> urls = new ArrayList<>();
		
		String[] paths = classPathString.split(";");
		for(String path:paths) {
			File f = new File(path);
			addFileToUrls(urls, f);				
			urls.add(f.getCanonicalFile().toURI().toURL());
		}
		return urls;
	}

	private static void addFilesToUrls(List<URL> urls, File f, FileFilter filter) throws IOException {
		if(f.isDirectory()) {
			for(File file:f.listFiles(filter)) {
				addFilesToUrls(urls, file, filter);
			}
		} else {
			addFileToUrls(urls, f);
		}
	}
	
	private static void addFileToUrls(List<URL> urls, File f) throws IOException {
		urls.add(f.getCanonicalFile().toURI().toURL());
	}
}
