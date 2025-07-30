package step.grid.client;

import org.junit.Test;
import step.grid.*;
import step.grid.filemanager.FileManagerException;
import step.grid.filemanager.FileVersion;
import step.grid.filemanager.FileVersionId;
import step.grid.tokenpool.Interest;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractGridClientImplTest {

    @Test
    public void testCallUnknownHost() {
        Grid grid = newGridMock("http://invalidUrlAbcdefg");
        GridClient client = newGridClient(grid);
        Exception exception = null;
        try {
            client.getTokenHandle(Map.of(), Map.of(), true);
        } catch (Exception e) {
            exception = e;
        }
        assertEquals("Failed to establish a connection to http://invalidUrlAbcdefg/token/test/release after 3 retries: jakarta.ws.rs.ProcessingException: java.net.UnknownHostException: invalidUrlAbcdefg", exception.getMessage());
    }

    private static AbstractGridClientImpl newGridClient(Grid grid) {
        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        gridClientConfiguration.setConnectionRetryGracePeriod(0);
        AbstractGridClientImpl client = new LocalGridClientImpl(gridClientConfiguration, grid);
        return client;
    }

    @Test
    public void testConnectionRefused() {
        Grid grid = newGridMock("http://127.0.0.1:1");
        GridClient client = newGridClient(grid);
        Exception exception = null;
        try {
            client.getTokenHandle(Map.of(), Map.of(), true);
        } catch (Exception e) {
            exception = e;
        }
        // We're forced to use startsWith as the error message contains more information on windows: "Connection refused: no further information"
        assertTrue(exception.getMessage().startsWith("Failed to establish a connection to http://127.0.0.1:1/token/test/release after 3 retries: jakarta.ws.rs.ProcessingException: java.net.ConnectException: Connection refused"));
    }

    private static Grid newGridMock(String agentUrl) {
        return new Grid() {

            @Override
            public FileVersion registerFile(File file, boolean cleanable) throws FileManagerException {
                return null;
            }

            @Override
            public void releaseFile(FileVersion fileVersion) {

            }

            @Override
            public FileVersion getRegisteredFile(FileVersionId fileVersionId) throws FileManagerException {
                return null;
            }

            @Override
            public void unregisterFile(FileVersionId fileVersionId) {

            }

            @Override
            public FileVersion registerFile(InputStream inputStream, String fileName, boolean isDirectory, boolean cleanable) throws FileManagerException {
                return null;
            }

            @Override
            public TokenWrapper selectToken(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout, long noMatchTimeout, TokenWrapperOwner tokenOwner) throws TimeoutException, InterruptedException {
                Token token = new Token();
                token.setId("test");
                token.setAgentid("test");
                AgentRef agent = new AgentRef();
                agent.setAgentUrl(agentUrl);
                return new TokenWrapper(token, agent);
            }

            @Override
            public void returnToken(String id) {

            }

            @Override
            public List<TokenWrapper> getTokens() {
                return null;
            }

            @Override
            public List<AgentRef> getAgents() {
                return null;
            }

            @Override
            public void markTokenAsFailing(String tokenId, String errorMessage, Exception e) {

            }

            @Override
            public void removeTokenError(String tokenId) {

            }

            @Override
            public void startTokenMaintenance(String tokenId) {

            }

            @Override
            public void stopTokenMaintenance(String tokenId) {

            }

            @Override
            public void invalidateToken(String tokenId) {

            }

            @Override
            public void cleanupFileManagerCache() {

            }
        };
    }
}