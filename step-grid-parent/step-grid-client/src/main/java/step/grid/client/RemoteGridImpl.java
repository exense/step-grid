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
package step.grid.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import step.commons.helpers.FileHelper;
import step.grid.Grid;
import step.grid.SelectTokenArgument;
import step.grid.TokenWrapper;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.tokenpool.Interest;
import step.grid.tokenpool.Token;

public class RemoteGridImpl implements Grid {

	protected String gridHost;
	
	private Client client;
	
	protected Map<String, NewCookie> cookies;
	
	int connectionTimeout;
	
	protected RemoteGridImpl(String gridHost) {
		this.gridHost = gridHost;
		
		client = ClientBuilder.newClient();
		client.register(ObjectMapperResolver.class);
		client.register(JacksonJsonProvider.class);
		client.register(MultiPartFeature.class);
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
		Builder b = target.request();
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
			long noMatchTimeout) throws TimeoutException, InterruptedException {
		Builder r = requestBuilder("/grid/token/select");
		SelectTokenArgument selectTokenArgument = new SelectTokenArgument(attributes, interests, matchTimeout, noMatchTimeout);
		return executeRequest(()->r.post(Entity.entity(selectTokenArgument, MediaType.APPLICATION_JSON), TokenWrapper.class));
	}

	@Override
	public void returnToken(TokenWrapper object) {
		Builder r = requestBuilder("/grid/token/return");
		executeRequest(()->r.post(Entity.entity(object, MediaType.APPLICATION_JSON)));
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Token<TokenWrapper>> getTokens() {
		Builder r = requestBuilder("/grid/token/list");
		return executeRequest(()->r.get(List.class));
	}

	@Override
	public void markTokenAsFailing(String tokenId, String errorMessage, Exception e) {
		Builder r = requestBuilder("/grid/token/"+tokenId+"/error/add");
		executeRequest(()->r.post(Entity.entity(errorMessage, MediaType.APPLICATION_JSON)));
	}

	@Override
	public FileVersion registerFile(File file) throws FileManagerException {
		File fileToBeSent;
		boolean isDirectory;
		if(file.isDirectory()) {
			File tempFile;
			try {
				tempFile = Files.createTempFile(file.getName(), "").toFile();
				FileHelper.zipDirectory(file, tempFile);
			} catch (IOException e) {
				throw new FileManagerException(null, "Error while creating zio of directory "+file.getAbsolutePath(), e);
			}
			fileToBeSent = tempFile;
			isDirectory = true;
		} else {
			fileToBeSent = file;
			isDirectory = false;
		}
		FileDataBodyPart fileDataBodyPart = new FileDataBodyPart("file", fileToBeSent, MediaType.APPLICATION_OCTET_STREAM_TYPE);
		return registerFile(fileDataBodyPart, isDirectory);
	}

	protected FileVersion registerFile(FormDataBodyPart formDataBodyPart, boolean isDirectory) {
		MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        multiPart.bodyPart(formDataBodyPart);
        
        HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put("type", isDirectory?"dir":"file");
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
	public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory) throws FileManagerException {
		StreamDataBodyPart bodyPart = new StreamDataBodyPart("file", inputStream, fileName);
		return registerFile(bodyPart, isDirectory);
	}

}
