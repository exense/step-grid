package step.grid.filemanager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import step.commons.helpers.FileHelper;

public class FileManagerClientImplTest {

	protected File registryFolder;
	protected FileManagerClientImpl f;
	protected AtomicInteger callCount;
	protected FileVersionId fileVersionId1;
	protected FileVersion fileVersion1;
	
	@Before
	public void before() throws IOException {
		File tempFile1 = FileHelper.createTempFile();
		fileVersionId1 = new FileVersionId("f1", 1);
		fileVersion1 = new FileVersion(tempFile1, new FileVersionId("f1", 1), false);
		
		registryFolder = FileHelper.createTempFolder();
		
		callCount = new AtomicInteger();
		FileVersionProvider fileProvider = new FileVersionProvider() {
			
			@Override
			public FileVersion saveFileVersionTo(FileVersionId fileVersionId, File file) throws FileManagerException {
				File target = new File(file.getAbsolutePath()+"/"+fileVersion1.file.getName());
				try {
					Files.copy(fileVersion1.file.toPath(), target.toPath());
				} catch (IOException e) {
					throw new FileManagerException(fileVersionId, e);
				}
				callCount.incrementAndGet();
				return new FileVersion(target, fileVersionId, false);
			}
		};
		
		File tempFolder = FileHelper.createTempFolder();
		f = new FileManagerClientImpl(tempFolder, fileProvider);
	}
	
	/**
	 * Test the {@link FileManagerImpl} with a {@link FileVersionProvider} which 
	 * is responsible for the retrieval of the FileVersion if absent of the cache
	 * 
	 * @throws FileManagerException
	 * @throws IOException
	 */
	@Test
	public void testFileProvider() throws FileManagerException, IOException {
		FileVersion fileVersionActual1 = f.requestFileVersion(fileVersionId1);
		Assert.assertEquals(1, callCount.get());
		Assert.assertNotNull(fileVersionActual1);
		
		fileVersionActual1 = f.requestFileVersion(fileVersionId1);
		Assert.assertNotNull(fileVersionActual1);
		Assert.assertEquals(1, callCount.get());
		
		f.removeFileVersionFromCache(fileVersionId1);
		// assert that the file has been deleted
		Assert.assertFalse(fileVersionActual1.getFile().exists());
	}
	
}
