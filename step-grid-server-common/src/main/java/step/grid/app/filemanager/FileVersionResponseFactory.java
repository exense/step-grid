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
import step.grid.filemanager.FileVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Builds a controller-compatible HTTP {@link Response} streaming the content of a cached {@link FileVersion}.
 * <p>
 * This produces the exact same wire format as the grid server's {@code GET /grid/file/{id}/{version}} endpoint
 * (a {@code content-disposition} header of the form {@code attachment; filename = <name>; type = dir|file}),
 * so that any downstream consumer (a forked agent, or a proxied agent) can be served transparently regardless
 * of whether it ultimately fetches from the controller, a grid proxy or a main agent.
 * <p>
 * A directory may be stored either <b>exploded</b> (a real directory on disk, e.g. an executing agent's cache),
 * in which case it is zipped on the fly, or already <b>archived</b> as a single zip file (the grid server, and
 * serve-only client caches), in which case it is streamed as-is. Both cases produce the {@code type = dir} wire
 * format. The form is detected from the on-disk artifact, so no caller needs to know how it was stored.
 * <p>
 * The usage lock on the served version is released only once the streaming completes (on success, error, or
 * client disconnect), via the {@code releaser} callback. The caller must therefore acquire the version with
 * usage tracking so it can't be evicted mid-stream; tying the release to the end of the {@link StreamingOutput}
 * keeps the usage count above zero for the whole transfer, which makes parallel serving safe (including with an
 * immediate-eviction TTL of 0) and prevents a cleanup sweep from deleting the file mid-stream.
 */
public class FileVersionResponseFactory {

    private FileVersionResponseFactory() {
    }

    /**
     * Builds a response streaming the given {@link FileVersion} and releasing it once the streaming completes.
     *
     * @param fileVersion the version to stream (acquired with usage tracking)
     * @param releaser    releases the version's usage lock; invoked once the transfer finishes or fails, e.g.
     *                    {@code fileManager::releaseFileVersion} / {@code fileManagerClient::releaseFileVersion}
     */
    public static Response buildFileResponse(FileVersion fileVersion, Consumer<FileVersion> releaser) {
        File file = fileVersion.getFile();
        boolean isDirectory = fileVersion.isDirectory();
        // A directory stored exploded (a real directory on disk) is zipped on the fly; a directory stored
        // archived (a single zip file) and plain files are streamed as-is.
        boolean zipOnTheFly = isDirectory && file.isDirectory();
        StreamingOutput fileStream = output -> {
            try {
                if (zipOnTheFly) {
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
                releaser.accept(fileVersion);
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
            .header("content-disposition", "attachment; filename = " + file.getName() + "; type = " + (isDirectory ? "dir" : "file"))
            .build();
    }
}
