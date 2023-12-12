package step.grid;

import ch.exense.commons.io.FileHelper;
import org.junit.After;
import org.junit.Before;
import step.grid.agent.AbstractGridTest;
import step.grid.agent.Agent;
import step.grid.agent.conf.AgentConf;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.filemanager.FileManagerConfiguration;
import step.grid.filemanager.FileManagerImplConfig;
import step.grid.proxy.GridProxy;
import step.grid.proxy.conf.GridProxyConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
        conf.setGridProxyUrl("http://localhost:31001");
        conf.setGridUrl("http://localhost:"+grid.getServerPort());
        gridProxy = new GridProxy(conf);

        AgentConf agentConf = new AgentConf("http://localhost:31001", 0, null, 100);
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

}
