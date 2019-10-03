/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.grid;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import ch.exense.commons.io.FileHelper;
import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.FileManager;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;

@Path("/grid")
public class GridServices {

	@Inject
	GridImpl grid;
	
	@Inject
	FileManager fileManager;

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/register")
	public void register(RegistrationMessage message) {
		grid.handleRegistrationMessage(message);
	}
	
	@GET
    @Path("/file/{id}/{version}")
	public Response getFile(@PathParam("id") String id, @PathParam("version") String version) throws IOException, FileManagerException {
		FileVersionId versionId = new FileVersionId(id, version);
		FileVersion fileVersion = fileManager.getFileVersion(versionId);

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
				.header("content-disposition", "attachment; filename = "+file.getName()+"; type = "+(fileVersion.isDirectory()?"dir":"file")).build();
	}
	
	@POST
    @Path("/token/select")
	public TokenWrapper selectToken(SelectTokenArgument argument) throws TimeoutException, InterruptedException {
		return grid.selectToken(argument.attributes, argument.interests, argument.matchTimeout, argument.noMatchTimeout, argument.tokenOwner);
	}

	@POST
    @Path("/token/return")
	public void returnToken(String id) {
		grid.returnToken(id);
	}

	@GET
    @Path("/token/list")
	public List<TokenWrapper> getTokens() {
		return grid.getTokens();
	}

	@POST
    @Path("/token/{id}/error/add")
	public void markTokenAsFailing(@PathParam("id") String tokenId, String errorMessage) {
		grid.markTokenAsFailing(tokenId, errorMessage, null);
	}

	@POST
	@Path("/file/register")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public FileVersion registerFile(@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail, @QueryParam("type") String contentType) throws FileManagerException {
		if (uploadedInputStream == null || fileDetail == null)
			throw new RuntimeException("Invalid arguments");
		
		return grid.registerFile(uploadedInputStream, fileDetail.getFileName(), contentType!=null && contentType.equals("dir"));
	}
	
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

	@POST
	@Path("/file/unregister")
	@Consumes(MediaType.APPLICATION_JSON)
	public void unregisterFile(FileVersionId fileVersionId) throws FileManagerException {
		grid.unregisterFile(fileVersionId);
	}
}
