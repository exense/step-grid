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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
                File target = new File(file.getAbsolutePath() + "/" + fileVersion1.file.getName());
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
        fileManagerConfiguration.setCleanupTimeToLiveMinutes(ttlMs);
        fileManagerConfiguration.setCleanupFrequencyMinutes(cleanupIntervalMs);
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

    /**
     * AC-2: when several threads request the same (fileId, version) concurrently, only one download is
     * performed. The other threads block on and reuse the result of the active download.
     */
    @Test
    public void testConcurrentRequestsTriggerSingleDownload() throws Exception {
        CountDownLatch enteredDownload = new CountDownLatch(1);
        CountDownLatch releaseDownload = new CountDownLatch(1);
        fileProvider = (fileVersionId, container) -> {
            callCount.incrementAndGet();
            enteredDownload.countDown();
            try {
                releaseDownload.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return copyInto(container, fileVersionId);
        };
        initFileManagerClient(3600000, 3600000);

        int nThreads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        try {
            List<Future<FileVersion>> futures = new ArrayList<>();
            for (int i = 0; i < nThreads; i++) {
                futures.add(pool.submit((Callable<FileVersion>) () -> fileManagerClient.requestFileVersion(fileVersionId1, true)));
            }
            // Wait for the single writer to be inside the download, then give the other threads time to pile up
            Assert.assertTrue(enteredDownload.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            releaseDownload.countDown();
            for (Future<FileVersion> future : futures) {
                Assert.assertNotNull(future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        Assert.assertEquals(1, callCount.get());
        Assert.assertEquals(1, fileManagerFolder.list().length);
    }

    /**
     * Crash-safety: a temporary container left behind by an interrupted download (e.g. a crash) is ignored and
     * cleaned up when the cache is loaded, and a fresh request still succeeds.
     */
    @Test
    public void testLeftoverTempFolderCleanedOnLoad() throws Exception {
        File tempContainerRoot = new File(fileManagerFolder, ".tmp");
        File tempContainer = new File(tempContainerRoot, fileVersionId1.getFileId() + "-" + fileVersionId1.getVersion() + "-abc123");
        Assert.assertTrue(tempContainer.mkdirs());
        Assert.assertTrue(new File(tempContainer, "partial.dat").createNewFile());

        initFileManagerClient(3600000, 3600000); // triggers loadCache()

        Assert.assertFalse("Leftover temporary download folder must be removed on load", tempContainerRoot.exists());

        FileVersion fileVersion = fileManagerClient.requestFileVersion(fileVersionId1, true);
        Assert.assertNotNull(fileVersion);
        Assert.assertEquals(1, callCount.get());
        Assert.assertTrue(fileVersion.getFile().exists());
    }

    private FileVersion copyInto(File container, FileVersionId fileVersionId) throws FileManagerException {
        File target = new File(container.getAbsolutePath() + "/" + fileVersion1.file.getName());
        try {
            Files.copy(fileVersion1.file.toPath(), target.toPath());
        } catch (IOException e) {
            throw new FileManagerException(fileVersionId, e);
        }
        return new FileVersion(target, fileVersionId, false);
    }

}
