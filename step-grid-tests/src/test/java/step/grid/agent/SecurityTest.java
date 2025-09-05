/*
 * Copyright (C) 2025, exense GmbH
 *
 * This file is part of Step
 *
 * Step is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Step is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Step.  If not, see <http://www.gnu.org/licenses/>.
 */

package step.grid.agent;

import ch.exense.commons.io.FileHelper;
import org.junit.Test;
import step.grid.AgentRef;
import step.grid.GridImpl;
import step.grid.TokenWrapper;
import step.grid.agent.conf.AgentConf;
import step.grid.client.AbstractGridClientImpl;
import step.grid.client.GridClientConfiguration;
import step.grid.client.RemoteClientException;
import step.grid.client.RemoteGridClientImpl;
import step.grid.client.security.ClientSecurityConfiguration;
import step.grid.security.SecurityConfiguration;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

public class SecurityTest {

    public static final String GRID_SERVICES_SECRET = "MAKFt7x4ILsVi3krtF/INHMBj7jxU2alQyvAla/cA1w=";
    public static final String AGENT_SERVICES_SECRET = "0YOBzko5spoWMWgSchsMaOOdKFiMLa2nDZbebaQ8qxQ=";

    @Test
    public void test() throws Exception {
        GridImpl grid = startProtectedGrid();
        String gridHost = "http://127.0.0.1:" + grid.getServerPort();
        // Start an agent without client security configured. This agent shouldn't be able to register to the grid
        try(Agent agent = startAgentWithoutClientSecurity(gridHost)) {
            // Create a remote grid client without client security. This grid client shouldn't be able to call the grid
            try(RemoteGridClientImpl gridClient1 = new RemoteGridClientImpl(gridClientConfigurationWithoutSecurity(), gridHost)) {
                RemoteClientException remoteClientException = assertThrows(RemoteClientException.class, () -> gridClient1.getTokenHandle(Map.of(), Map.of(), false));
                assertTrue(remoteClientException.getMessage().contains("Missing or invalid Authorization header"));

            }

            // Create a remote grid client with the proper client security configuration.
            try(RemoteGridClientImpl gridClient = new RemoteGridClientImpl(gridClientConfigurationWithoutSecurity(), gridHost, remoteGridClientSecurity())) {
                Exception exception = assertThrows(Exception.class, () -> gridClient.getTokenHandle(Map.of(), Map.of(), false));
                // Assert that no token is present i.e. that the agent couldn't register
                assertTrue(exception.getMessage().contains("Request failed"));
            }
        }

        // Start the agent with the proper client security. This agent should be able to register itself into the grid
        try(Agent agent = startAgentWithProperClientSecurity(gridHost)) {
            try(RemoteGridClientImpl gridClient = new RemoteGridClientImpl(gridClientWithSecurity(), gridHost, remoteGridClientSecurity())) {
                TokenWrapper tokenHandle = gridClient.getTokenHandle(Map.of(), Map.of(), false);
                // Ensure that the agent token is available i.e. that the agent registered successfully
                assertNotNull(tokenHandle);
                AgentRef agentRef = tokenHandle.getAgent();
                gridClient.pingAgent(agentRef);
                gridClient.shutdownAgent(agentRef);
            }
        }

        try(Agent agent = startProtectedAgent(gridHost)) {
            try(RemoteGridClientImpl gridClient = new RemoteGridClientImpl(gridClientConfigurationWithoutSecurity(), gridHost, remoteGridClientSecurity())) {
                TokenWrapper tokenHandle = gridClient.getTokenHandle(Map.of(), Map.of(), false);
                // Ensure that the agent token is available i.e. that the agent registered successfully
                assertNotNull(tokenHandle);
                AgentRef agentRef = tokenHandle.getAgent();
                // The ping service isn't protected and should work
                gridClient.pingAgent(agentRef);
                // The shutdown service is protected and should fail because
                AbstractGridClientImpl.AgentSideException agentSideException = assertThrows(AbstractGridClientImpl.AgentSideException.class, () -> gridClient.shutdownAgent(agentRef));
                assertTrue(agentSideException.getMessage().contains("Missing or invalid Authorization header"));
            }
        }

        try(Agent agent = startProtectedAgent(gridHost)) {
            try(RemoteGridClientImpl gridClient = new RemoteGridClientImpl(gridClientWithSecurity(), gridHost, remoteGridClientSecurity())) {
                TokenWrapper tokenHandle = gridClient.getTokenHandle(Map.of(), Map.of(), false);
                // Ensure that the agent token is available i.e. that the agent registered successfully
                assertNotNull(tokenHandle);
                AgentRef agentRef = tokenHandle.getAgent();
                // The shutdown service should now be reachable as the client is properly configured with the right secret
                gridClient.shutdownAgent(agentRef);
            }
        }
    }

    private static GridClientConfiguration gridClientWithSecurity() {
        GridClientConfiguration gridClientConfiguration = gridClientConfigurationWithoutSecurity();
        gridClientConfiguration.setSecurity(new ClientSecurityConfiguration(AGENT_SERVICES_SECRET));
        return gridClientConfiguration;
    }

    private static GridClientConfiguration gridClientConfigurationWithoutSecurity() {
        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        // This timeout should be high enough to let the agent start
        gridClientConfiguration.setNoMatchExistsTimeout(3000);
        return gridClientConfiguration;
    }

    private static ClientSecurityConfiguration remoteGridClientSecurity() {
        return new ClientSecurityConfiguration(GRID_SERVICES_SECRET);
    }

    private Agent startProtectedAgent(String gridHost) throws Exception {
        AgentConf agentConf = new AgentConf();
        agentConf.setSecurity(new SecurityConfiguration(true, AGENT_SERVICES_SECRET));
        agentConf.setRegistrationClientSecurity(new ClientSecurityConfiguration(GRID_SERVICES_SECRET));
        return startAgent(gridHost, agentConf);
    }

    private static Agent startAgentWithoutClientSecurity(String gridHost) throws Exception {
        AgentConf agentConf = new AgentConf();
        return startAgent(gridHost, agentConf);
    }

    private static Agent startAgentWithProperClientSecurity(String gridHost) throws Exception {
        AgentConf agentConf = new AgentConf();
        agentConf.setRegistrationClientSecurity(new ClientSecurityConfiguration(GRID_SERVICES_SECRET));
        return startAgent(gridHost, agentConf);
    }

    private static Agent startAgent(String gridHost, AgentConf agentConf) throws Exception {
        agentConf.setGridHost(gridHost);
        Agent agent = new Agent(agentConf);
        agent.addTokens(1, Map.of(), Map.of(), Map.of());
        return agent;
    }

    private static GridImpl startProtectedGrid() throws Exception {
        File tempFolder = FileHelper.createTempFolder();
        tempFolder.deleteOnExit();
        GridImpl.GridImplConfig gridConfig = new GridImpl.GridImplConfig();
        gridConfig.setSecurity(new SecurityConfiguration(true, GRID_SERVICES_SECRET));
        GridImpl grid = new GridImpl(tempFolder, 0, gridConfig);
        grid.start();
        return grid;
    }
}
