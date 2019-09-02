package step.grid.contextbuilder;

import java.io.File;
import java.io.IOException;

import step.grid.filemanager.FileManagerException;

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
	public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileManagerException {
		try {
			return new JavaLibrariesClassLoader(libFolder, parentClassLoader);
		} catch (IOException e) {
			throw new FileManagerException(null, e);
		}
	}

}
