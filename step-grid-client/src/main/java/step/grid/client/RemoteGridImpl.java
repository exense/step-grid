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
package step.grid.client;

import ch.exense.commons.io.FileHelper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import step.grid.*;
import step.grid.client.security.JwtTokenGenerator;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.security.SymmetricSecurityConfiguration;
import step.grid.tokenpool.Interest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class RemoteGridImpl implements Grid {

	private final JwtTokenGenerator jwtTokenGenerator;
	private final SymmetricSecurityConfiguration gridSecurityConfiguration;
	protected String gridHost;
	
	private Client client;
	
	protected Map<String, NewCookie> cookies;
	
	int connectionTimeout;
	
	protected RemoteGridImpl(String gridHost, SymmetricSecurityConfiguration gridSecurityConfiguration) {
		this.gridHost = gridHost;
		this.gridSecurityConfiguration = gridSecurityConfiguration;
		
		client = ClientBuilder.newClient();
		client.register(GridObjectMapperResolver.class);
		client.register(JacksonJsonProvider.class);
		client.register(MultiPartFeature.class);

		jwtTokenGenerator = JwtTokenGenerator.initializeJwtTokenGenerator(gridSecurityConfiguration, "remote grid client");
	}
	
	protected Builder requestBuilder(String path) {
		return requestBuilder(path, null);
	}
	
	protected Builder requestBuilder(String path, Map<String, String> queryParams) {
		WebTarget target = client.target(gridHost + path);
		if(queryParams!=null) {
			for(String key:queryParams.keySet()) {
				target=target.queryParam(key, queryParams.get(key));
			}
		}
		Builder b = JwtTokenGenerator.withAuthentication(jwtTokenGenerator, target.request());
		b.accept(MediaType.APPLICATION_JSON);
		if(cookies!=null) {
			for(NewCookie c:cookies.values()) {
				b.cookie(c);
			}			
		}
		return b;
	}
	
	protected <T> T executeRequest(Supplier<T> provider) throws RemoteClientException {
		try {
			T r = provider.get();
			if(r instanceof Response) {
				Response response = (Response) r;
				if(!(response.getStatus()==204||response.getStatus()==200)) {
					String error = response.readEntity(String.class);
					throw new RemoteClientException("Error while calling "+
							gridHost+". The server returned following error: "+error);
				} else {
					return r;
				}
			} else {
				return r;
			}
		} catch(WebApplicationException e) {
			String errorMessage = e.getResponse().readEntity(String.class);
			throw new RemoteClientException("Error while calling "+
			gridHost+". The server returned following error: "+errorMessage, e);
		}
	}

	@Override
	public TokenWrapper selectToken(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout,
			long noMatchTimeout, TokenWrapperOwner tokenOwner) throws TimeoutException, InterruptedException {
		Builder r = requestBuilder("/grid/token/select");
		SelectTokenArgument selectTokenArgument = new SelectTokenArgument(attributes, interests, matchTimeout, noMatchTimeout, tokenOwner);
		return executeRequest(()->r.post(Entity.entity(selectTokenArgument, MediaType.APPLICATION_JSON), TokenWrapper.class));
	}

	@Override
	public void returnToken(String id) {
		Builder r = requestBuilder("/grid/token/return");
		executeRequest(()->r.post(Entity.entity(id, MediaType.APPLICATION_JSON)));
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<TokenWrapper> getTokens() {
		Builder r = requestBuilder("/grid/token/list");
		return executeRequest(()->r.get(new GenericType<List<TokenWrapper>>() {}));
	}

	@Override
	public List<AgentRef> getAgents() {
		Builder r = requestBuilder("/grid/agent/list");
		return executeRequest(()->r.get(new GenericType<List<AgentRef>>() {}));
	}

	@Override
	public void markTokenAsFailing(String tokenId, String errorMessage, Exception e) {
		Builder r = requestBuilder("/grid/token/"+tokenId+"/error/add");
		executeRequest(()->r.post(Entity.entity(errorMessage, MediaType.APPLICATION_JSON)));
	}

	@Override
	public void removeTokenError(String tokenId) {
		Builder r = requestBuilder("/grid/token/"+tokenId+"/error");
		executeRequest(()->r.delete());
	}

	@Override
	public void startTokenMaintenance(String tokenId) {
		Builder r = requestBuilder("/grid/token/"+tokenId+"/maintenance");
		executeRequest(()->r.post(null));
	}

	@Override
	public void stopTokenMaintenance(String tokenId) {
		Builder r = requestBuilder("/grid/token/"+tokenId+"/maintenance");
		executeRequest(()->r.delete());
	}

	@Override
	public void invalidateToken(String tokenId) {
		Builder r = requestBuilder("/grid/token/invalidate");
		executeRequest(()->r.post(Entity.entity(tokenId, MediaType.APPLICATION_JSON)));
	}

	@Override
	public void cleanupFileManagerCache() {
		throw new RuntimeException("NotImplementedException"); //NotImplementedException();
	}

	@Override
	public FileVersion registerFile(File file, boolean cleanable) throws FileManagerException {
		File fileToBeSent;
		boolean isDirectory;
		if(file.isDirectory()) {
			File tempFile;
			try {
				tempFile = Files.createTempFile(file.getName(), "").toFile();
				FileHelper.zip(file, tempFile);
			} catch (IOException e) {
				throw new FileManagerException(null, "Error while creating zip of directory "+file.getAbsolutePath(), e);
			}
			fileToBeSent = tempFile;
			isDirectory = true;
		} else {
			fileToBeSent = file;
			isDirectory = false;
		}
		FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file", fileToBeSent, MediaType.APPLICATION_OCTET_STREAM_TYPE);
		return registerFile(fileDataBodyPart, isDirectory, cleanable);
	}

	@Override
	public void releaseFile(FileVersion fileVersion) {
		Builder r = requestBuilder("/grid/file/release");
		executeRequest(()->r.post(Entity.entity(fileVersion, MediaType.APPLICATION_JSON)));
	}

	protected FileVersion registerFile(FormDataBodyPart formDataBodyPart, boolean isDirectory, boolean cleanable) {
		MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        multiPart.bodyPart(formDataBodyPart);
        
        HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put("type", isDirectory?"dir":"file");
		queryParams.put("cleanable", Boolean.toString(cleanable));
        Builder b = requestBuilder("/grid/file/register", queryParams);
        return executeRequest(()->b.post(Entity.entity(multiPart, multiPart.getMediaType()), FileVersion.class));
	}

	@Override
	public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
		throw new RuntimeException("Not supported");
	}

	@Override
	public void unregisterFile(FileVersionId fileVersionId) {
		 Builder b = requestBuilder("/grid/file/unregister");
	     executeRequest(()->b.post(Entity.entity(fileVersionId, MediaType.APPLICATION_JSON)));
	}

	@Override
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory, boolean cleanable) throws FileManagerException {
		StreamDataBodyPart bodyPart = new StreamDataBodyPart("file", inputStream, fileName);
		return registerFile(bodyPart, isDirectory, cleanable);
	}

	@Override
	public SymmetricSecurityConfiguration getSecurityConfiguration() {
		return gridSecurityConfiguration;
	}
}
