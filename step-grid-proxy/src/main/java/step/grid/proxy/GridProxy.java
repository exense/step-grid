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
import ch.exense.commons.io.FileHelper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.jetty.server.Server;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.Token;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GridProxy extends BaseServer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GridProxy.class);
    public static final String TOKEN_ATTRIBUTE_GRID_PROXY_NAME = "$gridProxyName";
    public static final String DEFAULT_GRID_PROXY_NAME = "UnnamedGridProxy";
    private final Server server;

    private final ConcurrentHashMap<String, String> agentUrlToContextRoot = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> contextRootToAgentUrl = new ConcurrentHashMap<>();
    private final String gridProxyUrl;
    private final String gridProxyName;
    private final GridProxyConfiguration configuration;
    private Client client;

    private final String gridUrl;
    private final Integer gridConnectTimeout;
    private final Integer gridReadTimeout;
    private final Integer agentConnectTimeout;
    private final Integer agentReserveTimeout;
    private final Integer agentReleaseTimeout;

    public static void main(String[] args) throws Exception {
        GridProxy gridProxy = new GridProxy(args);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try {
                gridProxy.close();
            } catch (Exception e) {
                logger.error("Error while closing the grid proxy", e);
            }
        }));
    }

    private static GridProxyConfiguration validateArgumentsAndReadConfiguration(String[] args, Class<? extends GridProxyConfiguration> configurationClass) throws Exception {
        ArgumentParser arguments = new ArgumentParser(args);
        String gridProxyConfigPath = arguments.getOption("config");
        if(gridProxyConfigPath!=null) {
            return readConfiguration(arguments, gridProxyConfigPath, configurationClass);
        } else {
            throw new RuntimeException("Argument '-config' is missing.");
        }
    }

    private static GridProxyConfiguration readConfiguration(ArgumentParser arguments, String gridProxyConfigPath, Class<? extends GridProxyConfiguration> configurationClass) throws Exception {
        ConfigurationParser<GridProxyConfiguration> gridProxyConfigurationConfigurationParser = new ConfigurationParser<>();
        return gridProxyConfigurationConfigurationParser.parse(arguments, new File(gridProxyConfigPath), (Class<GridProxyConfiguration>) configurationClass);
    }

    public GridProxy(String[] args) throws Exception {
        this(args, GridProxyConfiguration.class);
    }

    public GridProxy(String[] args, Class<? extends GridProxyConfiguration> configurationClass) throws Exception {
        this(validateArgumentsAndReadConfiguration(args, configurationClass));
    }

    public GridProxy(GridProxyConfiguration gridProxyConfiguration) throws Exception {
        this.configuration = gridProxyConfiguration;
        int serverPort = this.resolveServerPort(gridProxyConfiguration.getGridProxyUrl(), gridProxyConfiguration.getGridProxyPort());
        if (configuration.getGridProxyName() == null) {
            logger.warn("The grid proxy name isn't set in the proxy configuration (field 'gridProxyName'). Falling back to default name {}.", DEFAULT_GRID_PROXY_NAME);
            gridProxyName = DEFAULT_GRID_PROXY_NAME;
        } else {
            gridProxyName = configuration.getGridProxyName();
        }
        logger.info("Starting grid proxy [name={}, localPort={}]...", gridProxyName, serverPort);
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.packages(GridProxyServices.class.getPackage().getName());
        final GridProxy gridProxy = this;
        resourceConfig.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(gridProxy).to(GridProxy.class);
            }
        });

        beforeServerStart(resourceConfig);
        server = this.startServer(gridProxyConfiguration, serverPort, resourceConfig);

        int actualServerPort = this.getActualServerPort(server);
        logger.info("Successfully started grid proxy on port " + actualServerPort);

        gridUrl = gridProxyConfiguration.getGridUrl();
        //Determine the actual Url of the grid proxy based either on configuration or actual system hostname and port
        gridProxyUrl = this.getOrBuildActualUrl(gridProxyConfiguration.getGridProxyHost(),
                gridProxyConfiguration.getGridProxyUrl(),
                actualServerPort, gridProxyConfiguration.isSsl());

        //Create REST client
        client = ClientBuilder.newClient();
        client.register(JacksonJsonProvider.class);
        gridConnectTimeout = gridProxyConfiguration.getGridConnectTimeout();
        gridReadTimeout = gridProxyConfiguration.getGridReadTimeout();
        agentConnectTimeout = gridProxyConfiguration.getAgentConnectTimeout();
        agentReserveTimeout = gridProxyConfiguration.getAgentReserveTimeout();
        agentReleaseTimeout = gridProxyConfiguration.getAgentReleaseTimeout();

        afterStart();
    }

    protected void beforeServerStart(ResourceConfig resourceConfig) throws Exception {}
    
    protected void afterStart() throws Exception {}

    public String getGridUrl() {
        return gridUrl;
    }

    public String getGridProxyUrl() {
        return gridProxyUrl;
    }

    public String getGridProxyName() {
        return gridProxyName;
    }

    public GridProxyConfiguration getConfiguration() {
        return configuration;
    }

    //Allow override for junits
    protected void overrideRestClient(Client client) {
        this.client = client;
    }

    public void handleRegistrationMessage(RegistrationMessage message) throws MalformedURLException {
        String agentUrl = message.getAgentRef().getAgentUrl();
        // replace by proxyfied url (proxy base url + context root) and maintain the mapping
        message.getAgentRef().setAgentUrl(gridProxyUrl + "/" + getContextRoot(agentUrl));
        message.getAgentRef().setLocalAgentUrl(agentUrl);
        // adding the name of the proxy to the token attributes in order to allow token selection by proxy name
        Optional.ofNullable(message.getTokens()).ifPresent(tokens -> tokens.forEach(token ->
                Optional.ofNullable(token.getAttributes()).ifPresent(attributes -> attributes.put(TOKEN_ATTRIBUTE_GRID_PROXY_NAME, gridProxyName))));
        //Forward registration message to grid server
        try (Response r = client.target(gridUrl + "/grid/register").request().property(ClientProperties.READ_TIMEOUT, gridReadTimeout)
                    .property(ClientProperties.CONNECT_TIMEOUT, gridConnectTimeout).post(Entity.entity(message, MediaType.APPLICATION_JSON))) {

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
        String contextRoot = agentUrlToContextRoot.get(agentUrl);
        if (contextRoot == null) {
            URL url = new URL(agentUrl);
            String host = url.getHost();
            int port = url.getPort();
            contextRoot = host + "-" + port;
            agentUrlToContextRoot.put(agentUrl, contextRoot);
            contextRootToAgentUrl.put(contextRoot, agentUrl);
            logger.info("Registering new agent from url '" + agentUrl + "' with contextRoot: " + contextRoot);
            return contextRoot;
        }
        return contextRoot;
    }

    public Response forwardGetFileRequest(String fileId, String version) throws IOException {
        Response fromGrid = null;
        try {
            fromGrid = client.target(gridUrl + "/grid/file/" + fileId + "/" + version).request().property(ClientProperties.READ_TIMEOUT, gridReadTimeout)
                    .property(ClientProperties.CONNECT_TIMEOUT, gridConnectTimeout).get();

            //Shallow copy to properly clean up the resources of the invocation to the grid server
            final InputStream inputStream = fromGrid.readEntity(InputStream.class);

            StreamingOutput oupputStream = output -> {
                try {
                    FileHelper.copy(inputStream, output, 2048);
                    output.flush();
                } finally {
                    inputStream.close();
                }
            };

            Response.ResponseBuilder responseBuilder = Response.fromResponse(fromGrid);
            return responseBuilder.entity(oupputStream).build();
        } catch (Exception e) {
            //In case of exception, explicitly close the resource. For successful calls the inputStream/outputStream are closed when consumed
            if (fromGrid != null) {
                fromGrid.close();
            }
            throw e;
        }
    }

    public void reserveToken(String agentContext, String tokenId) {
        forwardToAgent(agentContext, "reserve", tokenId, agentReserveTimeout);
    }

    public void releaseToken(String agentContext, String tokenId) {
        forwardToAgent(agentContext, "release", tokenId, agentReleaseTimeout);
    }

    public OutputMessage forwardMessageToAgent(String agentContext, String operation, String tokenId, InputMessage message) {
        String agentUrl = contextRootToAgentUrl.get(agentContext);
        if (agentUrl != null) {
            try (Response response = client.target(agentUrl + "/token/" + tokenId + "/" + operation).request()
                    .property(ClientProperties.READ_TIMEOUT, agentConnectTimeout + message.getCallTimeout())
                    .property(ClientProperties.CONNECT_TIMEOUT, agentConnectTimeout).post(Entity.entity(message, MediaType.APPLICATION_JSON))) {
                return response.readEntity(OutputMessage.class);
            }
        } else {
            logger.error("Received request for unknown agent Id " + agentContext);
            throw new RuntimeException("Agent not registered to the Grid Proxy");
        }
    }

    public void forwardToAgent(String agentContext, String operation, String tokenId, Integer callTimeout) {
        String agentUrl = contextRootToAgentUrl.get(agentContext);
        if (agentUrl != null) {
            //Event if the variable is not used, use try-with-resource construct to make use resources are cleaned-up
            try (Response response = client.target(agentUrl + "/token/" + tokenId + "/" + operation).request()
                    .property(ClientProperties.READ_TIMEOUT, callTimeout)
                    .property(ClientProperties.CONNECT_TIMEOUT, agentConnectTimeout).get()) {
            }
        } else {
            logger.error("Received request for unknown agent Id " + agentContext);
            throw new RuntimeException("Agent not registered to the Grid Proxy");
        }
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    @Override
    public void close() throws Exception {
        if (server != null && !server.isStopped()) {
            server.stop();
        }
    }

}