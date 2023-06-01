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
package step.grid.agent;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

import ch.exense.commons.io.FileHelper;
import step.grid.filemanager.ControllerCallException;
import step.grid.filemanager.ControllerCallTimeout;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.filemanager.FileVersionProvider;

public class RegistrationClient implements FileVersionProvider {
	
	private final String registrationServer;
	private final String fileServer;
	
	private Client client;
	
	private static final Logger logger = LoggerFactory.getLogger(RegistrationClient.class);

	int connectionTimeout;
	int callTimeout;

	public RegistrationClient(String registrationServer, String fileServer, int connectionTimeout, int callTimeout) {
		super();
		this.registrationServer = registrationServer;
		this.fileServer = fileServer;
		this.client = ClientBuilder.newClient();
		this.client.register(ObjectMapperResolver.class);
		this.client.register(JacksonJsonProvider.class);
		this.callTimeout = callTimeout;
		this.connectionTimeout = connectionTimeout;
	}
	
	public boolean sendRegistrationMessage(RegistrationMessage message) {
		try {
			Response r = client.target(registrationServer + "/grid/register").request().property(ClientProperties.READ_TIMEOUT, callTimeout)
					.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout).post(Entity.entity(message, MediaType.APPLICATION_JSON));
			
			r.readEntity(String.class);
			return true;
		} catch (ProcessingException e) {
			if(e.getCause() instanceof java.net.ConnectException) {
				logger.error("Unable to reach " + registrationServer + " for agent registration (java.net.ConnectException: "+e.getCause().getMessage()+")");				
			} else {
				logger.error("while registering tokens to " + registrationServer, e);				
			}
			return false;
		}
	}

	public void close() {
		client.close();
	}

	@Override
	public FileVersion saveFileVersionTo(FileVersionId fileVersionId, File container) throws FileManagerException {
		try {
			Response response;
			try {
				response = client.target(fileServer + "/grid/file/"+fileVersionId.getFileId()+"/"+fileVersionId.getVersion()).request().property(ClientProperties.READ_TIMEOUT, callTimeout)
						.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout).get();
			} catch (ProcessingException e) {
				Throwable cause = e.getCause();
				if(cause!=null && cause instanceof SocketTimeoutException) {
					String causeMessage =  cause.getMessage();
					if(causeMessage.contains("Read timed out")) {
						throw new ControllerCallTimeout(e, callTimeout);
					} else {
						throw new ControllerCallException(e);
					}
				} else {
					throw new ControllerCallException(e);
				}
			}
			if(response.getStatus()!=200) {
				String error = response.readEntity(String.class);
				throw new RuntimeException("Unexpected server error: "+error);
			} else {
				InputStream in = (InputStream) response.getEntity();
				boolean isDirectory = response.getHeaderString("content-disposition").contains("dir");
				Matcher m = Pattern.compile(".*filename = (.+?);.*").matcher(response.getHeaderString("content-disposition"));
				if(m.find()) {
					String filename = m.group(1);
					
					long t2 = System.currentTimeMillis();
					File file = new File(container+"/"+filename);
					if(isDirectory) {
						FileHelper.unzip(in, file);
					} else {
						BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
						FileHelper.copy(in, bos, 1024);
						bos.close();					
					}
					if(logger.isDebugEnabled()) {
						logger.debug("Uncompressed file "+ fileVersionId +" in "+(System.currentTimeMillis()-t2)+"ms to "+file.getAbsoluteFile());
					}
					
					FileVersion fileVersion = new FileVersion(file, fileVersionId, isDirectory);
					return fileVersion;				
				} else {
					throw new RuntimeException("Unable to find filename in header: "+response.getHeaderString("content-disposition"));
				}
			}
		} catch(Exception e) {
			throw new FileManagerException(fileVersionId, e);
		}
	}
}
