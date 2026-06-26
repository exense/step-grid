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
import step.grid.client.HttpFileVersionProvider;
import step.grid.client.security.JwtTokenGenerator;
import step.grid.filemanager.*;
import step.grid.security.SymmetricSecurityConfiguration;

import java.io.File;
import java.util.List;


public class RegistrationClient implements FileVersionProvider {

    private final String registrationServer;
    private final JwtTokenGenerator jwtTokenGenerator;

    private final Client client;
    private final HttpFileVersionProvider fileVersionProvider;

    private static final Logger logger = LoggerFactory.getLogger(RegistrationClient.class);

    int connectionTimeout;
    int callTimeout;
    int maxRetries;
    int retryDelayMs;

    public RegistrationClient(String registrationServer, String fileServer, int connectionTimeout, int callTimeout,
                              int maxRetries, int retryDelayMs, SymmetricSecurityConfiguration gridSecurityConfiguration) {
        super();
        this.registrationServer = registrationServer;
        this.client = ClientBuilder.newClient();
        this.client.register(ObjectMapperResolver.class);
        this.client.register(JacksonJsonProvider.class);
        this.callTimeout = callTimeout;
        this.connectionTimeout = connectionTimeout;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;

        jwtTokenGenerator = JwtTokenGenerator.initializeJwtTokenGenerator(gridSecurityConfiguration, "registration client");
        // Delegate the actual artifact download to the shared HTTP file version provider
        this.fileVersionProvider = new HttpFileVersionProvider(client, fileServer, jwtTokenGenerator,
            connectionTimeout, callTimeout, maxRetries, retryDelayMs);
    }

    public boolean sendRegistrationMessage(RegistrationMessage message) {
        try {
            Response r = withAuthentication(client.target(registrationServer + "/grid/register").request()).property(ClientProperties.READ_TIMEOUT, callTimeout)
                .property(ClientProperties.CONNECT_TIMEOUT, connectionTimeout).post(Entity.entity(message, MediaType.APPLICATION_JSON));

            r.readEntity(String.class);
            return true;
        } catch (ProcessingException e) {
            if (e.getCause() instanceof java.net.ConnectException) {
                logger.error("Unable to reach " + registrationServer + " for agent registration (java.net.ConnectException: " + e.getCause().getMessage() + ")");
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
        return fileVersionProvider.saveFileVersionTo(fileVersionId, container);
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
