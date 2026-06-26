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

    public static Response buildFileResponse(FileVersion fileVersion) {
        File file = fileVersion.getFile();
        boolean isDirectory = fileVersion.isDirectory();
        StreamingOutput fileStream = output -> {
            if (isDirectory) {
                // The cached directory is stored unzipped, re-zip it to match the controller wire format
                FileHelper.zip(file, output);
            } else {
                try (InputStream inputStream = new FileInputStream(file)) {
                    FileHelper.copy(inputStream, output, 2048);
                }
            }
            output.flush();
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
            .header("content-disposition", "attachment; filename = " + file.getName() + "; type = " + (isDirectory ? "dir" : "file"))
            .build();
    }
}
