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
