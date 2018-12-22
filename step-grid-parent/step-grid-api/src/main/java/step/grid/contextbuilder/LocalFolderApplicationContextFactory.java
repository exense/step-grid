package step.grid.contextbuilder;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * This {@link ApplicationContextFactory} builds a classloader based on a folder
 * of jars. The resulting classloader will load all the jars contained in the specified
 * folder
 *
 */
public class LocalFolderApplicationContextFactory extends ApplicationContextFactory {

	protected File libFolder;

	public LocalFolderApplicationContextFactory(File libFolder) {
		super();
		this.libFolder = libFolder;
	}

	@Override
	public String getId() {
		return libFolder.getAbsolutePath();
	}

	@Override
	public boolean requiresReload() {
		return false;
	}

	@Override
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) {
		List<URL> urls = ClassPathHelper.forAllJarsInFolder(libFolder);
		
		URL[] urlArray = urls.toArray(new URL[urls.size()]);
		ClassLoader classLoader = new URLClassLoader(urlArray, parentClassLoader);
		return classLoader;
	}

}
