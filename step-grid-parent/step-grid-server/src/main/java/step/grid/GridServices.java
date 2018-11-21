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

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import step.grid.agent.RegistrationMessage;
import step.grid.filemanager.FileProvider;
import step.grid.filemanager.FileProvider.TransportableFile;

@Path("/grid")
public class GridServices {

	@Inject
	Grid grid;
	
	@Inject
	FileProvider fileManager;

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/register")
	public void register(RegistrationMessage message) {
		grid.handleRegistrationMessage(message);
	}
	
	@GET
    @Path("/file/{id}")
	public Response getFile(@PathParam("id") String id) throws IOException {
		TransportableFile file = fileManager.getTransportableFile(id);

		StreamingOutput fileStream = new StreamingOutput() {
			@Override
			public void write(java.io.OutputStream output) throws IOException {
				output.write(file.getBytes());
				output.flush();
			}
		};
		return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
				.header("content-disposition", "attachment; filename = "+file.getName()+"; type = "+(file.isDirectory()?"dir":"file")).build();
	}
}
