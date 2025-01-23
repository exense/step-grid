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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ch.exense.commons.io.FileHelper;

public class FileManagerImplTest {

	protected File registryFolder;
	protected TestFileManagerImpl fileManager;
	private FileManagerImplConfig config;

	@Before
	public void before() throws IOException {
		registryFolder = FileHelper.createTempFolder();
		config = new FileManagerImplConfig();
		// disable caching
		config.setFileLastModificationCacheExpireAfter(0);
		//disable file cache cleanup, it is enabled for individual tests
		config.setCleanupEnabled(false);
		config.setConfigurationTimeUnit(TimeUnit.MILLISECONDS);
		fileManager = new TestFileManagerImpl(registryFolder, config);
	}
	
	@After
	public void after() throws Exception {
		fileManager.close();
		FileHelper.deleteFolder(registryFolder);
	}
	
	@Test
	public void testFile() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		
		FileVersionId handle = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		FileVersion registeredFile = fileManager.getFileVersion(handle);
		Assert.assertNotNull(registeredFile);
		
		writeFileContent(testFile, "V2");

		FileVersionId handle2 = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		// Registering twice (this shouldn't throw any error)
		handle2 = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		Assert.assertFalse(handle2.equals(handle));
		
		FileVersion registeredFileHandle1 = fileManager.getFileVersion(handle);
		FileVersion registeredFileHandle2 = fileManager.getFileVersion(handle2);
		Assert.assertNotNull(registeredFileHandle2);
		
		Assert.assertEquals(registeredFileHandle1, registeredFile);
		Assert.assertFalse(registeredFileHandle1.equals(registeredFileHandle2));
	}
	
	@Test
	public void testStream() throws IOException, FileManagerException {
		FileVersionId handle = fileManager.registerFileVersion(this.getClass().getResourceAsStream("TestFile.txt"),"TestFile", false, false, true).getVersionId();;
		
		FileVersion fileVersion = fileManager.getFileVersion(handle);
		FileVersion registeredFile = fileVersion;
		Assert.assertNotNull(registeredFile);
		
		// register a different content under the same name "TestFile"
		FileVersionId handle2 = fileManager.registerFileVersion(this.getClass().getResourceAsStream("TestFile2.txt"),"TestFile", false, false, true).getVersionId();
		
		// register the same content under a different name
		FileVersionId handle3 = fileManager.registerFileVersion(this.getClass().getResourceAsStream("TestFile.txt"),"AnotherResourceName", false, false, true).getVersionId();;
		
		Assert.assertFalse(handle2.equals(handle));
		Assert.assertFalse(handle3.equals(handle));
		
		FileVersion registeredFileHandle1 = fileVersion;
		FileVersion registeredFileHandle2 = fileManager.getFileVersion(handle2);
		FileVersion registeredFileHandle3 = fileManager.getFileVersion(handle3);
		Assert.assertNotNull(registeredFileHandle2);
		Assert.assertFalse(registeredFileHandle2.isDirectory());
		Assert.assertEquals("Dummy content 2",Files.readAllLines(registeredFileHandle2.getFile().toPath()).get(0));
		
		Assert.assertNotNull(registeredFileHandle3);
		Assert.assertFalse(registeredFileHandle3.isDirectory());
		Assert.assertEquals("Dummy content 1",Files.readAllLines(registeredFileHandle3.getFile().toPath()).get(0));
		
		Assert.assertEquals(registeredFileHandle1, registeredFile);
		Assert.assertFalse(registeredFileHandle1.equals(registeredFileHandle2));
		
		fileManager.unregisterFileVersion(handle);
		registeredFileHandle1 = fileManager.getFileVersion(handle);
		Assert.assertNull(registeredFileHandle1);
	}
	
	@Test
	public void testMultipleVersions() throws IOException, FileManagerException {
		// Create a single empty file
		File testFile = FileHelper.createTempFile();
		
		// Register a first version of the file
		FileVersionId version1 = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		// Register a second version of the file
		writeFileContent(testFile, "V2");
		FileVersionId version2 = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		// Delete the source file. As soon as registered, it shouldn't be needed anymore
		testFile.delete();
		
		// Assert the first version is still available
		FileVersion fileVersion1 = fileManager.getFileVersion(version1);
		Assert.assertEquals("", new String(Files.readAllBytes(fileVersion1.getFile().toPath())));
		
		// Retrieve the version 2
		FileVersion fileVersion2 = fileManager.getFileVersion(version2);
		Assert.assertEquals("V2", new String(Files.readAllBytes(fileVersion2.getFile().toPath())));
		
		// Register a third version of the file and DELETE the other versions
		writeFileContent(testFile, "V3");
		FileVersionId version3 = fileManager.registerFileVersion(testFile, true, true).getVersionId();
		
		FileVersion fileVersion3 = fileManager.getFileVersion(version3);
		Assert.assertEquals("V3", new String(Files.readAllBytes(fileVersion3.getFile().toPath())));

		// the old versions should have been deleted
		Assert.assertNull(fileManager.getFileVersion(version1));
		Assert.assertNull(fileManager.getFileVersion(version2));
	}

	@Test
	public void testUnregistration() throws IOException, FileManagerException {
		// Create a single empty file
		File testFile = FileHelper.createTempFile();
		
		// Register a first version of the file
		FileVersionId version1 = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		// Register the same file a second time
		FileVersionId version2 = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		// Delete the source file. As soon as registered, it shouldn't be needed anymore
		testFile.delete();
		
		Assert.assertEquals(version1, version2);
		
		// Request deletion of the file version
		fileManager.unregisterFileVersion(version1);
			
		// The file is still not available anymore
		Assert.assertNull(fileManager.getFileVersion(version1));
	}
	
	private void writeFileContent(File testFile, String content) throws IOException {
		// Sleep 100ms to ensure that the file lastmodification's date get updated
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {}
		
		try(FileWriter w = new FileWriter(testFile)) {
			w.write(content);
		}
	}

	
	@Test
	public void testDirectory() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		
		FileVersionId handle = fileManager.registerFileVersion(testFile, false, true).getVersionId();
		
		FileVersion file = fileManager.getFileVersion(handle);
		Assert.assertNotNull(file);
	}
	
	@Test
	public void testCleanup() throws IOException, FileManagerException, InterruptedException {
		config.setCleanupLastAccessTimeThresholdMinutes(1);

		File testFile = FileHelper.createTempFile();
		File testFile2 = FileHelper.createTempFile();

		FileVersion fileVersion = fileManager.registerFileVersion(testFile, false, true);
		FileVersion fileVersion1 = fileManager.registerFileVersion(testFile2, false, true);

		fileManager.releaseFileVersion(fileVersion);
		fileManager.releaseFileVersion(fileVersion1);

		Assert.assertEquals(2, registryFolder.list().length);
		Thread.sleep(2);
		fileManager.cleanupCache();
		Assert.assertEquals(0, registryFolder.list().length);

		fileVersion = fileManager.registerFileVersion(testFile, false, true);
		fileVersion1 = fileManager.registerFileVersion(testFile2, false, false);
		fileManager.releaseFileVersion(fileVersion);
		fileManager.releaseFileVersion(fileVersion1);
		Assert.assertEquals(2, registryFolder.list().length);
		Thread.sleep(2);
		fileManager.cleanupCache();
		Assert.assertEquals(1, registryFolder.list().length);
	}

	@Test
	public void testCleanupJob() throws IOException, FileManagerException, InterruptedException {
		config.setCleanupLastAccessTimeThresholdMinutes(200);
		config.setCleanupIntervalMinutes(100);
		config.setCleanupEnabled(true);
		fileManager.scheduleCleanupJob();//start the job

		File testFile = FileHelper.createTempFile();
		File testFile2 = FileHelper.createTempFile();

		FileVersion fileVersion = fileManager.registerFileVersion(testFile, false, true);
		FileVersion fileVersion1 = fileManager.registerFileVersion(testFile2, false, true);

		fileManager.releaseFileVersion(fileVersion);
		fileManager.releaseFileVersion(fileVersion1);

		Assert.assertEquals(2, registryFolder.list().length);
		Thread.sleep(100);//last access threshold is 200ms, files should still be in
		Assert.assertEquals(2, registryFolder.list().length);
		Thread.sleep(250);//job run every 100ms + 200 ms last access threshold, file should be cleaned up
		Assert.assertEquals(0, registryFolder.list().length);

		fileVersion = fileManager.registerFileVersion(testFile, false, true);
		fileVersion1 = fileManager.registerFileVersion(testFile2, false, false);
		fileManager.releaseFileVersion(fileVersion);
		fileManager.releaseFileVersion(fileVersion1);
		Assert.assertEquals(2, registryFolder.list().length);
		Thread.sleep(100);//last access threshold is 200ms, files should still be in
		Assert.assertEquals(2, registryFolder.list().length);
		Thread.sleep(250); // following check occasionally (seldom) fails when 200, so give it just a tad more time
		Assert.assertEquals(1, registryFolder.list().length);
	}

	@Test
	public void testCleanupJobParallel() throws IOException, FileManagerException, InterruptedException, ExecutionException {
		config.setCleanupLastAccessTimeThresholdMinutes(10);
		config.setCleanupIntervalMinutes(5);
		config.setCleanupEnabled(true);
		fileManager.scheduleCleanupJob();//start the job

		File testFile = FileHelper.createTempFile();
		File testFile2 = FileHelper.createTempFile();
		AtomicReference<String> keyPairValidationStringRef = new AtomicReference<String>();
		List<Future<?>> futures = new ArrayList<>();
		int nThreads = 5;
		int nIterations = 500;
		ExecutorService threadPool = Executors.newFixedThreadPool(nThreads);
		for (int i = 0; i < nThreads; i++) {
			Future<?> future = threadPool.submit(() -> {
				try {
					for (int j = 0; j < nIterations; j++) {
						FileVersion fileVersion = fileManager.registerFileVersion(testFile, false, true);
						FileVersion fileVersion1 = fileManager.registerFileVersion(testFile2, false, false);
						//decrement usage
						Thread.sleep(1);
						fileManager.releaseFileVersion(fileVersion);
						fileManager.releaseFileVersion(fileVersion1);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			futures.add(future);
		}
		threadPool.shutdown();
		threadPool.awaitTermination(10, TimeUnit.SECONDS);
		for (Future<?> future : futures) {
			future.get();
		}

		Assert.assertEquals(2, registryFolder.list().length);
		int fileStoreCount = fileManager.getStoreCount().get();
		//depends on the perf of the system, should be at least 2 and way below 5*500
		Assert.assertTrue( fileStoreCount >= 2 && fileStoreCount < 50 );
		Thread.sleep(300);
		Assert.assertEquals(1, registryFolder.list().length);
	}
	
	@Test
	public void testCacheReload() throws IOException, FileManagerException {
		File testFile = FileHelper.createTempFile();
		File testFolder = FileHelper.createTempFolder();
		
		FileVersion fileVersion1 = fileManager.registerFileVersion(testFile, false, true);
		FileVersion fileVersion2 = fileManager.registerFileVersion(testFolder, false, true);
		
		fileManager = new TestFileManagerImpl(registryFolder, new FileManagerImplConfig());
		FileVersion fileVersionActual1 = fileManager.getFileVersion(fileVersion1.getVersionId());
		FileVersion fileVersionActual2 = fileManager.getFileVersion(fileVersion2.getVersionId());
		
		Assert.assertEquals(fileVersion1, fileVersionActual1);
		Assert.assertEquals(fileVersion2, fileVersionActual2);
	}

	public static class TestFileManagerImpl extends FileManagerImpl {
		private AtomicInteger storeCount = new AtomicInteger(0);
		public TestFileManagerImpl(File cacheFolder, FileManagerImplConfig config) {
			super(cacheFolder, config);
		}

		@Override
		protected CachedFileVersion storeFile(File source, FileVersionId fileVersionId, boolean cleanable) throws FileManagerException, IOException {
			storeCount.incrementAndGet();
			CachedFileVersion cachedFileVersion = super.storeFile(source, fileVersionId, cleanable);
			return cachedFileVersion;
		}

		public AtomicInteger getStoreCount() {
			return storeCount;
		}
	}
	
}
