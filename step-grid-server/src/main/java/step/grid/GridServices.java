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
package step.grid;

import ch.exense.commons.io.FileHelper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.FileManager;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.security.Secured;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Path("/grid")
@Hidden
public class GridServices {

	@Inject
	GridImpl grid;
	
	@Inject
	FileManager fileManager;

	@Secured
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/register")
	public void register(RegistrationMessage message) {
		grid.handleRegistrationMessage(message);
	}

	@Secured
	@GET
    @Path("/file/{id}/{version}")
	public Response getFile(@PathParam("id") String id, @PathParam("version") String version) throws IOException, FileManagerException {
		FileVersionId versionId = new FileVersionId(id, version);
		FileVersion fileVersion = null;
		try {
			fileVersion = fileManager.getFileVersion(versionId);

			File file = fileVersion.getFile();
			FileInputStream inputStream = new FileInputStream(file);

			StreamingOutput fileStream = new StreamingOutput() {

				@Override
				public void write(OutputStream output) throws IOException, WebApplicationException {
					try {
						// This buffer size doesn't seem to have a significant effect on the performance
						FileHelper.copy(inputStream, output, 2048);
						output.flush();
					} finally {
						inputStream.close();
					}
				}
			};

			return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
					.header("content-disposition", "attachment; filename = " + file.getName() + "; type = " + (fileVersion.isDirectory() ? "dir" : "file")).build();
		} finally {
			if (fileVersion != null) {
				fileManager.releaseFileVersion(fileVersion);
			}
		}
	}

	@Secured
	@GET
	@Path("/agent/list")
	public List<AgentRef> getAgents() {
		return grid.getAgents();
	}

	@Secured
	@POST
    @Path("/token/select")
	public TokenWrapper selectToken(SelectTokenArgument argument) throws TimeoutException, InterruptedException {
		return grid.selectToken(argument.attributes, argument.interests, argument.matchTimeout, argument.noMatchTimeout, argument.tokenOwner);
	}

	@Secured
	@POST
    @Path("/token/return")
	public void returnToken(String id) {
		grid.returnToken(id);
	}

	@Secured
	@POST
	@Path("/token/invalidate")
	public void invalidateToken(String id) {
		grid.invalidateToken(id);
	}

	@Secured
	@GET
    @Path("/token/list")
	public List<TokenWrapper> getTokens() {
		return grid.getTokens();
	}

	@Secured
	@POST
    @Path("/token/{id}/error/add")
	public void markTokenAsFailing(@PathParam("id") String tokenId, String errorMessage) {
		grid.markTokenAsFailing(tokenId, errorMessage, null);
	}

	@Secured
	@DELETE
	@Path("/token/{id}/error")
	public void removeTokenError(@PathParam("id") String tokenId) {
		grid.removeTokenError(tokenId);
	}

	@Secured
	@POST
	@Path("/token/{id}/maintenance")
	public void startTokenMaintenance(@PathParam("id") String tokenId) {
		grid.startTokenMaintenance(tokenId);
	}

	@Secured
	@DELETE
	@Path("/token/{id}/maintenance")
	public void stopTokenMaintenance(@PathParam("id") String tokenId) {
		grid.stopTokenMaintenance(tokenId);
	}

	@Secured
	@POST
	@Path("/file/register")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public FileVersion registerFile(@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail, @QueryParam("type") String contentType,
			@QueryParam("cleanable") String cleanable) throws FileManagerException {
		if (uploadedInputStream == null || fileDetail == null)
			throw new RuntimeException("Invalid arguments");
		
		return grid.registerFile(uploadedInputStream, fileDetail.getFileName(), contentType!=null && contentType.equals("dir"),
				Boolean.parseBoolean(cleanable));
	}

	@Secured
	@POST
	@Path("/file/release")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public void releaseFile(FileVersion fileVersion) throws FileManagerException {
		grid.releaseFile(fileVersion);
	}

	@Secured
	@POST
	@Path("/file/content")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		FileVersion registeredFile = grid.getRegisteredFile(fileVersionId);
		StreamingOutput fileStream = new StreamingOutput() {
			@Override
			public void write(java.io.OutputStream output) throws IOException {
				Files.copy(registeredFile.getFile().toPath(), output);
			}
		};
		
		String resourceName = registeredFile.getFile().getName();
		String mimeType = "application/octet-stream";
		String contentDisposition = "attachment";
		String headerValue = String.format(contentDisposition+"; filename=\"%s\"", resourceName);
		return Response.ok(fileStream, mimeType).header("content-disposition", headerValue).build();
	}

	@Secured
	@POST
	@Path("/file/unregister")
	@Consumes(MediaType.APPLICATION_JSON)
	public void unregisterFile(FileVersionId fileVersionId) throws FileManagerException {
		grid.unregisterFile(fileVersionId);
	}
}
