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
package step.grid.filemanager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ch.exense.commons.io.FileHelper;


public class FileManagerClientImplTest {

	protected File fileManagerFolder;
	protected FileManagerClientImpl fileManagerClient;
	protected AtomicInteger callCount;
	protected FileVersionId fileVersionId1;
	protected FileVersion fileVersion1;
	private FileVersionProvider fileProvider;

	@Before
	public void before() throws IOException {
		File tempFile1 = FileHelper.createTempFile();
		fileVersionId1 = new FileVersionId("f1", "1");
		fileVersion1 = new FileVersion(tempFile1, new FileVersionId("f1", "1"), false);
		
		callCount = new AtomicInteger();
		fileProvider = new FileVersionProvider() {
			
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
		
		fileManagerFolder = FileHelper.createTempFolder();
	}

	private void initFileManagerClient(int ttlMs, int cleanupIntervalMs) {
		FileManagerConfiguration fileManagerConfiguration = new FileManagerConfiguration();
		//configure the cleanup schedule job for junit and start it
		fileManagerConfiguration.setConfigurationTimeUnit(TimeUnit.MILLISECONDS);
		fileManagerConfiguration.setCleanupLastAccessTimeThresholdMinutes(ttlMs);
		fileManagerConfiguration.setCleanupIntervalMinutes(cleanupIntervalMs);
		fileManagerClient = new FileManagerClientImpl(fileManagerFolder, fileProvider, fileManagerConfiguration);
	}
	
	@After
	public void after() throws IOException {
		FileHelper.deleteFolder(fileManagerFolder);
	}
	
	/**
	 * Test the {@link FileManager} with a {@link FileVersionProvider} which
	 * is responsible for the retrieval of the FileVersion if absent of the cache
	 * 
	 * @throws FileManagerException
	 * @throws IOException
	 */
	@Test
	public void testFileProvider() throws FileManagerException, IOException, InterruptedException {
		initFileManagerClient(200, 100);
		Assert.assertEquals(0, fileManagerFolder.list().length);
		FileVersion fileVersionActual1 = fileManagerClient.requestFileVersion(fileVersionId1, true);
		Assert.assertEquals(1, callCount.get());
		Assert.assertEquals(1, fileManagerFolder.list().length);
		Assert.assertNotNull(fileVersionActual1);
		
		fileVersionActual1 = fileManagerClient.requestFileVersion(fileVersionId1, true);
		Assert.assertNotNull(fileVersionActual1);
		Assert.assertEquals(1, callCount.get());

		Thread.sleep(300); //give time for cleanup job to run
		//File is used twice (2 calls to requestFileVersion), so usage needs to be decremented to allow cleanup
		Assert.assertEquals(1, fileManagerFolder.list().length);
		//Decrement usage and wait rerun of the job
		fileManagerClient.releaseFileVersion(fileVersionActual1);
		fileManagerClient.releaseFileVersion(fileVersionActual1);
		Thread.sleep(300);
		Assert.assertEquals(0, fileManagerFolder.list().length);

		fileVersionActual1 = fileManagerClient.requestFileVersion(fileVersionId1, true);
		Assert.assertNotNull(fileVersionActual1);
		Assert.assertEquals(2, callCount.get());
		Assert.assertEquals(1, fileManagerFolder.list().length);
		
		fileManagerClient.removeFileVersionFromCache(fileVersionId1);
		// assert that the file has been deleted
		Assert.assertFalse(fileVersionActual1.getFile().exists());
	}

	/**
	 * Test the {@link FileManager} with a {@link FileVersionProvider} which
	 * is responsible for the retrieval of the FileVersion if absent of the cache
	 *
	 * @throws FileManagerException
	 * @throws IOException
	 */
	@Test
	public void testFileProviderNoCacheTTL() throws FileManagerException, IOException, InterruptedException {
		initFileManagerClient(0, 3600000);
		Assert.assertEquals(0, fileManagerFolder.list().length);
		FileVersion fileVersionActual1 = fileManagerClient.requestFileVersion(fileVersionId1, true);
		Assert.assertEquals(1, callCount.get());
		Assert.assertEquals(1, fileManagerFolder.list().length);
		Assert.assertNotNull(fileVersionActual1);

		fileVersionActual1 = fileManagerClient.requestFileVersion(fileVersionId1, true);
		Assert.assertNotNull(fileVersionActual1);
		Assert.assertEquals(1, callCount.get());

		Thread.sleep(300); //give time for cleanup job to run
		//File is used twice (2 calls to requestFileVersion), so usage needs to be decremented to allow cleanup
		Assert.assertEquals(1, fileManagerFolder.list().length);
		//Decrement usage and wait rerun of the job
		fileManagerClient.releaseFileVersion(fileVersionActual1);
		fileManagerClient.releaseFileVersion(fileVersionActual1);
		//No sleep required, with TTL = 0 the file is deleted as soon as the usage is decremented to 0
		Assert.assertEquals(0, fileManagerFolder.list().length);

		fileVersionActual1 = fileManagerClient.requestFileVersion(fileVersionId1, true);
		Assert.assertNotNull(fileVersionActual1);
		Assert.assertEquals(2, callCount.get());
		Assert.assertEquals(1, fileManagerFolder.list().length);

		fileManagerClient.removeFileVersionFromCache(fileVersionId1);
		// assert that the file has been deleted
		Assert.assertFalse(fileVersionActual1.getFile().exists());
	}
	
}
