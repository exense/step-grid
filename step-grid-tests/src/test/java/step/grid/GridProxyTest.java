package step.grid;

import ch.exense.commons.io.FileHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import step.grid.agent.AbstractGridTest;
import step.grid.agent.Agent;
import step.grid.agent.conf.AgentConf;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.client.TestMessageHandler;
import step.grid.filemanager.FileManagerConfiguration;
import step.grid.filemanager.FileManagerImplConfig;
import step.grid.filemanager.FileVersionId;
import step.grid.io.OutputMessage;
import step.grid.proxy.GridProxy;
import step.grid.proxy.conf.GridProxyConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GridProxyTest extends GridTest {

    private GridProxy gridProxy;

    @Before
    public void init() throws Exception {
        File fileManagerFolder = FileHelper.createTempFolder();

        GridImpl.GridImplConfig gridConfig = new GridImpl.GridImplConfig();
        // disable last modification cache
        FileManagerImplConfig fileManagerImplConfig = new FileManagerImplConfig();
        fileManagerImplConfig.setFileLastModificationCacheExpireAfter(0);
        gridConfig.setFileManagerImplConfig(fileManagerImplConfig);
        grid = new GridImpl(fileManagerFolder, 0, gridConfig);
        grid.start();

        GridProxyConfiguration conf = new GridProxyConfiguration();
        // Bind to a random free port instead of a hardcoded one to avoid clashes with OS-reserved port ranges
        conf.setGridProxyHost("localhost");
        conf.setGridProxyPort(0);
        conf.setGridUrl("http://localhost:" + grid.getServerPort());
        // Use an isolated temporary cache directory so the proxy cache doesn't leak between test runs
        conf.setFileCacheDirectory(FileHelper.createTempFolder().getAbsolutePath());
        gridProxy = new GridProxy(conf);
        String gridProxyUrl = gridProxy.getGridProxyUrl();

        AgentConf agentConf = new AgentConf(gridProxyUrl, 0, null, 100);
        agentConf.setGracefulShutdownTimeout(100l);
        agentConf.setFileManagerConfiguration(new FileManagerConfiguration());
        agent = new Agent(agentConf);
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("att1", "val1");
        agent.addTokens(nTokens, attributes, null, null);

        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        gridClientConfiguration.setNoMatchExistsTimeout(2000);
        client = new LocalGridClientImpl(gridClientConfiguration, grid);
        // wait for the agent to connect:
        Thread.sleep(1000);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        if (gridProxy != null) {
            gridProxy.close();
        }
    }

    /**
     * Fetching a directory artifact through the proxy: the proxy caches the directory <b>archived</b> (raw, no
     * explode) and re-serves it verbatim with a {@code type = dir} response, and the agent (behind the proxy)
     * explodes it locally. Verifies the whole raw-storage / re-serve path end-to-end by reading back the
     * transferred folder's structure.
     */
    @Test
    public void testFolderRegistrationThroughProxy() throws Exception {
        TokenWrapper token = selectToken();

        // Create a simple directory structure
        File tempDir = FileHelper.createTempFolder();
        File subFolder = new File(tempDir + "/SubFolder1");
        subFolder.mkdirs();
        new File(subFolder + "/File1").createNewFile();
        new File(tempDir + "/File1").createNewFile();

        // Register the directory on the grid; the agent downloads it from the proxy, which caches it archived
        FileVersionId fileHandle = client.registerFile(tempDir, true).getVersionId();

        JsonNode node = new ObjectMapper().createObjectNode().put("folder", fileHandle.getFileId()).put("fileVersion", fileHandle.getVersion());

        // TestMessageHandler resolves the folder version (exploded by the agent) and returns its structure
        OutputMessage output = client.call(token.getID(), node, TestMessageHandler.class.getName(), null, new HashMap<>(), 100000);
        client.returnTokenHandle(token.getID());

        List<String> content = Arrays.stream(output.getPayload().get("content").asText().split(";")).sorted().collect(Collectors.toList());
        List<String> expected = Arrays.stream("SubFolder1;File1;".split(";")).sorted().collect(Collectors.toList());
        Assert.assertEquals(expected, content);
    }

}
