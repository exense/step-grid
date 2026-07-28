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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import step.grid.TokenWrapper;
import step.grid.agent.conf.AgentForkerConfiguration;
import step.grid.bootstrap.ResourceExtractor;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.client.TestMessageHandler;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;
import step.grid.security.SymmetricSecurityConfiguration;

import java.io.File;
import java.util.HashMap;

/**
 * Asserts that the forked agent mode works when the JWT authentication of the grid is enabled. The grid secret
 * is shared by all the components of the topology, the forked agents included, and both directions of the
 * communication with a forked agent are protected:
 * <ul>
 *     <li>main agent to forked agent: the token is reserved, called and released on the forked agent, and the
 *     forked agent is finally shut down. All these services are secured.</li>
 *     <li>forked agent to main agent: the forked agent downloads the file required by the keyword from the file
 *     service of its main agent, which is secured as well.</li>
 * </ul>
 */
public class ForkedAgentSecurityTest extends AbstractGridTest {

    @Before
    public void init() throws Exception {
        secureGrid = true;
        AgentForkerConfiguration agentForkerConfiguration = new AgentForkerConfiguration();
        agentForkerConfiguration.enabled = true;
        super.init(agentForkerConfiguration);

        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        gridClientConfiguration.setGridSecurity(new SymmetricSecurityConfiguration(GRID_SECRET));
        client = new LocalGridClientImpl(gridClientConfiguration, grid);
    }

    @Test
    public void test() throws Exception {
        File testFile = ResourceExtractor.extractResource(this.getClass().getClassLoader(), "TestFile.txt");
        FileVersionId fileVersionId = client.registerFile(testFile, true).getVersionId();

        TokenWrapper token = selectToken();
        try {
            JsonNode message = new ObjectMapper().createObjectNode()
                .put("file", fileVersionId.getFileId())
                .put("fileVersion", fileVersionId.getVersion());
            // The keyword is executed in a forked agent and reads the content of the registered file, which the
            // forked agent has to download from its main agent
            OutputMessage output = client.call(token.getID(), message, TestMessageHandler.class.getName(), null, new HashMap<>(), 60000);
            Assert.assertNull(output.getAgentError());
            Assert.assertEquals("TEST", output.getPayload().get("content").asText());
        } finally {
            client.returnTokenHandle(token.getID());
        }
    }
}
