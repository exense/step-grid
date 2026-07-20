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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Builds a controller-compatible HTTP {@link Response} streaming the content of a cached {@link FileVersion}.
 * <p>
 * This produces the exact same wire format as the grid server's {@code GET /grid/file/{id}/{version}} endpoint
 * (a {@code content-disposition} header of the form {@code attachment; filename = <name>; type = dir|file}),
 * so that any downstream consumer (a forked agent, or a proxied agent) can be served transparently regardless
 * of whether it ultimately fetches from the controller, a grid proxy or a main agent.
 * <p>
 * Unlike the grid server, which stores directories as a single zip file, the file manager <i>client</i> stores
 * directories <b>unzipped</b>. Directories are therefore re-zipped on the fly when re-served.
 */
public class FileVersionResponseFactory {

    private FileVersionResponseFactory() {
    }

    /**
     * Builds a response streaming the given cached {@link FileVersion} and <b>releases the in-use lock held
     * on it once the streaming completes</b> (on success, on error, or on client disconnect).
     * <p>
     * The caller must have acquired the version with usage tracking enabled
     * ({@code requestFileVersion(..., trackUsage=true)}) so that the file cannot be evicted by the cleanup
     * job while it is being streamed. The content of the file is only read here, inside the
     * {@link StreamingOutput}, i.e. after the caller's resource method has returned and released any cache
     * lock. Tying the {@link FileManagerClient#releaseFileVersion(FileVersion)} call to the end of the stream
     * therefore keeps the usage count above zero for the whole duration of the transfer, which is what makes
     * parallel serving safe (including with an immediate-eviction TTL of 0) and prevents a cleanup sweep from
     * deleting the file mid-stream.
     *
     * @param fileManagerClient the client from which the version was requested, used to release the usage lock
     * @param fileVersion       the cached version to stream, acquired with {@code trackUsage=true}
     */
    public static Response buildFileResponse(FileManagerClient fileManagerClient, FileVersion fileVersion) {
        File file = fileVersion.getFile();
        boolean isDirectory = fileVersion.isDirectory();
        StreamingOutput fileStream = output -> {
            try {
                if (isDirectory) {
                    // The cached directory is stored unzipped, re-zip it to match the controller wire format
                    FileHelper.zip(file, output);
                } else {
                    try (InputStream inputStream = new FileInputStream(file)) {
                        FileHelper.copy(inputStream, output, 2048);
                    }
                }
                output.flush();
            } finally {
                // Release the usage lock only once the file has been fully streamed (or the transfer failed),
                // so the version stays protected from eviction for the entire duration of the transfer.
                fileManagerClient.releaseFileVersion(fileVersion);
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
            .header("content-disposition", "attachment; filename = " + file.getName() + "; type = " + (isDirectory ? "dir" : "file"))
            .build();
    }
}
