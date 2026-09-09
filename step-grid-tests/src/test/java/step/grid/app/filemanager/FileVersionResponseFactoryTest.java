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
package step.grid.app.filemanager;

import ch.exense.commons.io.FileHelper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.Assert;
import org.junit.Test;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FileVersionResponseFactoryTest {

    private static RecordingOutputStream stream(Response response) throws Exception {
        StreamingOutput streamingOutput = (StreamingOutput) response.getEntity();
        RecordingOutputStream out = new RecordingOutputStream();
        streamingOutput.write(out);
        return out;
    }

    /**
     * Records what the factory does to the stream it is handed. A {@link StreamingOutput} must not close the
     * response stream: the JAX-RS runtime owns it and closes it once the body has been written.
     */
    private static class RecordingOutputStream extends ByteArrayOutputStream {

        private int closeCount;

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
        }
    }

    /**
     * A plain file is streamed verbatim with a {@code type = file} disposition, and the usage lock is released
     * exactly once, after the streaming.
     */
    @Test
    public void testPlainFileIsStreamedAsIsAndReleasedAfterStreaming() throws Exception {
        File file = FileHelper.createTempFile();
        Files.write(file.toPath(), "hello".getBytes());
        FileVersion fileVersion = new FileVersion(file, new FileVersionId("f", "1"), false);
        AtomicInteger released = new AtomicInteger();

        Response response = FileVersionResponseFactory.buildFileResponse(fileVersion, fv -> released.incrementAndGet());
        // Not released before the body is streamed
        Assert.assertEquals(0, released.get());
        RecordingOutputStream out = stream(response);

        Assert.assertArrayEquals("hello".getBytes(), out.toByteArray());
        Assert.assertTrue(response.getHeaderString("content-disposition").contains("type = file"));
        Assert.assertEquals(1, released.get());
        Assert.assertEquals("the response stream must be left open for the JAX-RS runtime", 0, out.closeCount);
    }

    /**
     * A directory stored <b>archived</b> (a single zip file on disk, isDirectory=true) is streamed verbatim -
     * no re-zip - with a {@code type = dir} disposition. This is the serve-only cache / grid server path.
     */
    @Test
    public void testArchivedDirectoryIsStreamedAsIs() throws Exception {
        File dir = FileHelper.createTempFolder();
        new File(dir, "a.txt").createNewFile();
        File zip = new File(FileHelper.createTempFolder(), "mydir.zip");
        FileHelper.zip(dir, zip);
        FileVersion fileVersion = new FileVersion(zip, new FileVersionId("f", "1"), true);
        AtomicInteger released = new AtomicInteger();

        Response response = FileVersionResponseFactory.buildFileResponse(fileVersion, fv -> released.incrementAndGet());
        RecordingOutputStream out = stream(response);

        Assert.assertArrayEquals("archived directory must be streamed byte-for-byte", Files.readAllBytes(zip.toPath()), out.toByteArray());
        Assert.assertTrue(response.getHeaderString("content-disposition").contains("type = dir"));
        Assert.assertEquals(1, released.get());
        Assert.assertEquals("the response stream must be left open for the JAX-RS runtime", 0, out.closeCount);
    }

    /**
     * A directory stored <b>exploded</b> (a real directory on disk, isDirectory=true) is zipped on the fly with
     * a {@code type = dir} disposition. This is the defensive path for an executing consumer that re-serves.
     */
    @Test
    public void testExplodedDirectoryIsZippedOnTheFly() throws Exception {
        File dir = FileHelper.createTempFolder();
        new File(dir, "a.txt").createNewFile();
        new File(dir, "b.txt").createNewFile();
        FileVersion fileVersion = new FileVersion(dir, new FileVersionId("f", "1"), true);
        AtomicInteger released = new AtomicInteger();

        Response response = FileVersionResponseFactory.buildFileResponse(fileVersion, fv -> released.incrementAndGet());
        RecordingOutputStream out = stream(response);

        Assert.assertTrue(response.getHeaderString("content-disposition").contains("type = dir"));
        // The exploded directory was zipped on the fly: the output is a zip stream (PK magic), not a raw listing.
        byte[] bytes = out.toByteArray();
        Assert.assertTrue("expected a zip stream", bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K');
        Assert.assertEquals(1, released.get());
        // Zipping on the fly must not close the response stream either, even though the ZipOutputStream created
        // by FileHelper.zip is closed: only its finish() concerns us, the JAX-RS runtime closes the response.
        Assert.assertEquals("the response stream must be left open for the JAX-RS runtime", 0, out.closeCount);
        // ... and suppressing that close must not cost us the central directory the finish() writes: the archive
        // is complete and readable, entries included.
        File unzipped = FileHelper.createTempFolder();
        FileHelper.unzip(bytes, unzipped);
        Assert.assertEquals(Set.of("a.txt", "b.txt"), Set.of(Objects.requireNonNull(unzipped.list())));
    }
}
