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

import ch.exense.commons.io.FileHelper;
import ch.exense.commons.resilience.RetryHelper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.Token;
import step.grid.client.security.JwtTokenGenerator;
import step.grid.filemanager.*;
import step.grid.security.SymmetricSecurityConfiguration;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class RegistrationClient implements FileVersionProvider {

	public static final List<Class<? extends Exception>> RETRY_FOR_EXCEPTIONS = Stream.concat(
			RetryHelper.COMMON_NETWORK_EXCEPTIONS.stream(),
			Stream.of(ControllerCallException.class, ControllerCallTimeout.class)
	).collect(Collectors.toList());

	private final String registrationServer;
	private final String fileServer;
	private final JwtTokenGenerator jwtTokenGenerator;

	private final Client client;
	
	private static final Logger logger = LoggerFactory.getLogger(RegistrationClient.class);

	int connectionTimeout;
	int callTimeout;
	int maxRetries;
	int retryDelayMs;

	public RegistrationClient(String registrationServer, String fileServer, int connectionTimeout, int callTimeout,
							  int maxRetries, int retryDelayMs, SymmetricSecurityConfiguration gridSecurityConfiguration) {
		super();
		this.registrationServer = registrationServer;
		this.fileServer = fileServer;
		this.client = ClientBuilder.newClient();
		this.client.register(ObjectMapperResolver.class);
		this.client.register(JacksonJsonProvider.class);
		this.callTimeout = callTimeout;
		this.connectionTimeout = connectionTimeout;
		this.maxRetries = maxRetries;
		this.retryDelayMs = retryDelayMs;

		jwtTokenGenerator = JwtTokenGenerator.initializeJwtTokenGenerator(gridSecurityConfiguration, "registration client");
	}
	
	public boolean sendRegistrationMessage(RegistrationMessage message) {
		try {
			Response r = withAuthentication(client.target(registrationServer + "/grid/register").request()).property(ClientProperties.READ_TIMEOUT, callTimeout)
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

	public Invocation.Builder withAuthentication(Invocation.Builder requestBuilder) {
		return JwtTokenGenerator.withAuthentication(jwtTokenGenerator, requestBuilder);
	}

	public void close() {
		client.close();
	}

	@Override
	public FileVersion saveFileVersionTo(FileVersionId fileVersionId, File container) throws FileManagerException {
		try {
			return RetryHelper.executeWithRetryOnExceptions(
					() -> downloadAndSaveFileVersion(fileVersionId, container),
					maxRetries,
					retryDelayMs,
					RETRY_FOR_EXCEPTIONS,
					"Download file " + fileVersionId
			);
		} catch (Exception e) {
			throw new FileManagerException(fileVersionId, e);
		}
	}

	private FileVersion downloadAndSaveFileVersion(FileVersionId fileVersionId, File container) throws Exception {
		Response response;
		try {
			response = withAuthentication(client.target(fileServer + "/grid/file/"+fileVersionId.getFileId()+"/"+fileVersionId.getVersion()).request()).property(ClientProperties.READ_TIMEOUT, callTimeout)
					.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout).get();
		} catch (ProcessingException e) {
			Throwable cause = e.getCause();
			if(cause instanceof SocketTimeoutException) {
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
			String contentDisposition = response.getHeaderString("content-disposition");
			if (contentDisposition != null) {
				boolean isDirectory = contentDisposition.contains("type = dir");
				Matcher m = Pattern.compile(".*filename = (.+?);.*").matcher(contentDisposition);
				if (m.find()) {
					String filename = m.group(1);

					long t2 = System.currentTimeMillis();
					File file = new File(container + "/" + filename);
					if (isDirectory) {
						FileHelper.unzip(in, file);
					} else {
						try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
							FileHelper.copy(in, bos, 1024);
						}
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Uncompressed file " + fileVersionId + " in " + (System.currentTimeMillis() - t2) + "ms to " + file.getAbsoluteFile());
					}

					return new FileVersion(file, fileVersionId, isDirectory);
				} else {
					throw new RuntimeException("Unable to find filename in header: " + contentDisposition);
				}
			} else {
				throw new RuntimeException("No content-disposition header found in the HTTP response");
			}
		}
	}

	public void switchTokensToMaintenanceMode(List<Token> tokens) {
		tokens.forEach(token -> {
			try {
				withAuthentication(client.target(registrationServer + "/grid/token/" + token.getId() + "/maintenance").request()).property(ClientProperties.READ_TIMEOUT, callTimeout)
						.property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout).post(Entity.entity(null, MediaType.APPLICATION_JSON));
			} catch (ProcessingException e) {
				logger.error("Error while unregistering token " + token.getId() + " from grid", e);
			}
		});
	}
}
