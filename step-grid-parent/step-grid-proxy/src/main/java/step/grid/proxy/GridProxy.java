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
package step.grid.proxy;

import ch.exense.commons.app.ArgumentParser;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.server.Server;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.agent.RegistrationMessage;
import step.grid.app.configuration.ConfigurationParser;
import step.grid.app.server.BaseServer;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.proxy.conf.GridProxyConfiguration;
import step.grid.proxy.services.GridProxyServices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

public class GridProxy {

    private static final Logger logger = LoggerFactory.getLogger(GridProxy.class);
    private final Server server;

    private final ConcurrentHashMap<String, String> agentUrlToContextRoot = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> contextRootToAgentUrl = new ConcurrentHashMap<>();
    private final String gridProxyUrl;
    private Client client;

    private final String gridUrl;
    private final Integer connectTimeout;
    private final Integer readTimeout;

    public static void main(String[] args) throws Exception {
        newInstanceFromArgs(args);
    }

    public static GridProxy newInstanceFromArgs(String[] args) throws Exception {
        ArgumentParser arguments = new ArgumentParser(args);

        String gridProxyConfigPath = arguments.getOption("config");

        if(gridProxyConfigPath!=null) {
            ConfigurationParser<GridProxyConfiguration> gridProxyConfigurationConfigurationParser = new ConfigurationParser<>();
            GridProxyConfiguration config = gridProxyConfigurationConfigurationParser.parse(arguments, new File(gridProxyConfigPath), GridProxyConfiguration.class);
            return new GridProxy(config);
        } else {
            throw new RuntimeException("Argument '-config' is missing.");
        }
    }

    public GridProxy(GridProxyConfiguration gridProxyConfiguration) throws Exception {
        int serverPort = BaseServer.resolveServerPort(gridProxyConfiguration.getGridProxyUrl(), gridProxyConfiguration.getGridProxyPort());

        logger.info("Starting server...");
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.packages(GridProxyServices.class.getPackage().getName());
        final GridProxy gridProxy = this;
        resourceConfig.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(gridProxy).to(GridProxy.class);
            }
        });

        server = BaseServer.startServer(gridProxyConfiguration, serverPort, resourceConfig);

        int actualServerPort = BaseServer.getActualServerPort(server);
        logger.info("Successfully started server on port " + actualServerPort);

        gridUrl = gridProxyConfiguration.getGridUrl();
        //Determine the actual Url of the grid proxy based either on configuration or actual system hostname and port
        gridProxyUrl = BaseServer.getOrBuildActualUrl(gridProxyConfiguration.getGridProxyHost(),
                gridProxyConfiguration.getGridProxyUrl(),
                actualServerPort, gridProxyConfiguration.isSsl());

        //Create REST client
        client = ClientBuilder.newClient();
        client.register(JacksonJsonProvider.class);
        connectTimeout = gridProxyConfiguration.getGridConnectTimeout();
        readTimeout = gridProxyConfiguration.getGridReadTimeout();
    }

    //Allow override for junits
    protected void overrideRestClient(Client client) {
        this.client = client;
    }

    public void handleRegistrationMessage(RegistrationMessage message) throws MalformedURLException {
        String agentUrl = message.getAgentRef().getAgentUrl();
        // replace by proxyfied url (proxy base url + context root) and maintain the mapping
        message.getAgentRef().setAgentUrl(gridProxyUrl + "/" + getContextRoot(agentUrl));
        //Forward registration message to grid server
        try (Response r = client.target(gridUrl + "/grid/register").request().property(ClientProperties.READ_TIMEOUT, readTimeout)
                    .property(ClientProperties.CONNECT_TIMEOUT, connectTimeout).post(Entity.entity(message, MediaType.APPLICATION_JSON))) {

            r.readEntity(String.class);
        } catch (ProcessingException e) {
            if(e.getCause() instanceof java.net.ConnectException) {
                logger.error("Unable to reach " + gridUrl + " while proxyfying agent registration from " + agentUrl + " (java.net.ConnectException: "+e.getCause().getMessage()+")");
            } else {
                logger.error("Error while proxyfying registration request to " + gridUrl + " from " + agentUrl, e);
            }
            //Rethrow exception as response to the Agent
            throw e;
        }
    }

    /**
     * This method return the registered context root for the provided agent URL or generates a unique one for new registration
     * <p>Note: parsing to URL might cause performance issue, could be replaced by URL encoding otherwise</p>
     * <p>In case of malformed URL the exceptions is rethrown</p>
     *
     * @param agentUrl the agent Url used to generate the context root
     * @return the unique context root for this agent
     */
    private String getContextRoot(String agentUrl) throws MalformedURLException {
        //Not using computeIfAbsent for rethrowing MalformedURLException
        if (agentUrlToContextRoot.containsKey(agentUrl)) {
            return agentUrlToContextRoot.get(agentUrl);
        } else {
            URL url = new URL(agentUrl);
            String host = url.getHost();
            int port = url.getPort();
            String contextRoot = host + "-" + port;
            agentUrlToContextRoot.put(agentUrl, contextRoot);
            contextRootToAgentUrl.put(contextRoot, agentUrl);
            logger.info("Registering new agent from url '" + agentUrl + "' with contextRoot: " + contextRoot);
            return contextRoot;
        }
    }

    public Response forwardGetFileRequest(String fileId, String version) throws IOException {
        try (Response fromGrid = client.target(gridUrl +  "/grid/file/" + fileId + "/" + version).request().property(ClientProperties.READ_TIMEOUT, readTimeout)
                .property(ClientProperties.CONNECT_TIMEOUT, connectTimeout).get()) {
            //Shallow copy to properly clean up the resources of the invocation to the grid server
            final InputStream inputStream = fromGrid.readEntity(InputStream.class);
            Response.ResponseBuilder responseBuilder = Response.fromResponse(fromGrid);
            return responseBuilder.entity(inputStream.readAllBytes()).build();
        }
    }

    public OutputMessage forwardMessageToAgent(String agentContext, String operation, String tokenId, InputMessage message) {
        if (contextRootToAgentUrl.containsKey(agentContext)) {
            String agentUrl = contextRootToAgentUrl.get(agentContext);
            try (Response response = client.target(agentUrl + "/token/" + tokenId + "/" + operation).request()
                    .property(ClientProperties.READ_TIMEOUT, readTimeout + message.getCallTimeout())
                    .property(ClientProperties.CONNECT_TIMEOUT, connectTimeout).post(Entity.entity(message, MediaType.APPLICATION_JSON))) {
                return response.readEntity(OutputMessage.class);
            }
        } else {
            logger.error("Received request for unknown agent Id " + agentContext);
            throw new RuntimeException("Agent not registered to the Grid Proxy");
        }
    }

    public void forwardToAgent(String agentContext, String operation, String tokenId) {
        if (contextRootToAgentUrl.containsKey(agentContext)) {
            String agentUrl = contextRootToAgentUrl.get(agentContext);
            //Event if the variable is not used, use try-with-resource construct to make use resources are cleaned-up
            try (Response response = client.target(agentUrl + "/token/" + tokenId + "/" + operation).request()
                    .property(ClientProperties.READ_TIMEOUT, readTimeout)
                    .property(ClientProperties.CONNECT_TIMEOUT, connectTimeout).get()) {
            }
        } else {
            logger.error("Received request for unknown agent Id " + agentContext);
            throw new RuntimeException("Agent not registered to the Grid Proxy");
        }
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public void stop() throws Exception {
        if (server != null && !server.isStopped()) {
            server.stop();
        }
    }
}