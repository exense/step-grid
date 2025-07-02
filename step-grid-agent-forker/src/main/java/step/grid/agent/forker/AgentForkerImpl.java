package step.grid.agent.forker;

import ch.exense.commons.io.FileHelper;
import ch.exense.commons.processes.ExternalJVMLauncher;
import ch.exense.commons.processes.ManagedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.GridImpl;
import step.grid.TokenWrapper;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class AgentForkerImpl implements AgentForker {

    private static final Logger logger = LoggerFactory.getLogger(AgentForkerImpl.class);
    private final AgentForkerConfiguration configuration;
    private final GridImpl grid;
    private final String fileServerHost;
    private final Path forkedAgentConf;
    private ExternalJVMLauncher jvmLauncher;
    private LocalGridClientImpl gridClient;

    public AgentForkerImpl(AgentForkerConfiguration configuration, String fileServerHost) throws IOException {
        this.configuration = configuration;
        this.fileServerHost = fileServerHost;

        forkedAgentConf = Files.createTempFile("ForkedAgentConf", ".yaml");
        Files.write(forkedAgentConf, FileHelper.readResourceAsByteArray(AgentForkerSessionImpl.class, "ForkedAgentConf.yaml"), new OpenOption[]{StandardOpenOption.APPEND});

        if (configuration.enabled) {
            this.jvmLauncher = newJvmLauncher();
            this.grid = createGrid(0);
            this.gridClient = newClient();
        } else {
            this.grid = null;
        }
    }

    private static GridImpl createGrid(int port) {
        logger.info("Starting agent forker grid...");
        GridImpl forkedExecutionGrid = new GridImpl(new File("./filemanager"), port);
        try {
            forkedExecutionGrid.start();
        } catch (Exception e) {
            throw new RuntimeException("Error while starting sub-agent grid on port " + port, e);
        }
        logger.info("Started agent forker grid on port "+forkedExecutionGrid.getServerPort());
        return forkedExecutionGrid;
    }

    @Override
    public void close() throws Exception {
        if (grid != null) {
            logger.info("Stopping agent forker grid...");
            grid.stop();
        }
        if (gridClient != null) {
            logger.info("Stopping agent forker grid client...");
            gridClient.close();
        }
        forkedAgentConf.toFile().delete();
    }

    @Override
    public AgentForkerSessionImpl newSession() throws Exception {
        return new AgentForkerSessionImpl();
    }

    private ExternalJVMLauncher newJvmLauncher() {
        //try(ManagedProcess process = jvmLauncher.launchExternalJVM("AgentTokenProcessor" + tokenId, Agent.class, List.of("-agentlib:jdwp=transport=dt_socket,server=n,address=DESKTOP-7NQHKTC.home:5005,suspend=y"), List.of("-config=C:\\Users\\jecom\\Git\\step-grid\\step-grid-agent\\src\\test\\resources\\SubAgentConf.yaml"))) {
        return new ExternalJVMLauncher(configuration.javaPath, new File("."));
    }

    private LocalGridClientImpl newClient() {
        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        // Configure the selection timeout (this should be higher than the start time of the container)
        gridClientConfiguration.setNoMatchExistsTimeout(60_000);
        LocalGridClientImpl gridClient = new LocalGridClientImpl(gridClientConfiguration, grid);
        return gridClient;
    }

    @Override
    public boolean isEnabled() {
        return configuration.enabled;
    }

    private class AgentForkerSessionImpl implements AgentForkerSession {

        final ManagedProcess process;
        private final TokenWrapper tokenHandle;
        private final String id;

        public AgentForkerSessionImpl() throws Exception {
            id = UUID.randomUUID().toString();
            logger.info("Creating isolated session...");
            logger.info("Starting isolated agent in a separated JVM...");
            this.process = jvmLauncher.launchExternalJVM("IsolatedExecutionAgent", "step.grid.agent.Agent", List.of(), List.of("-config=" + forkedAgentConf.toAbsolutePath(), "-gridHost=http://localhost:" + grid.getServerPort(), "-fileServerHost=" + fileServerHost, "-isolatedSessionId=" + id), true);
            try {
                // Wait for the token to join the grid
                logger.info("Waiting for isolated agent to connect...");
                tokenHandle = gridClient.getTokenHandle(Map.of(), Map.of("isolatedSessionId", new Interest(Pattern.compile(id), true)), true);
            } catch (Throwable e) {
                try {
                    logger.info("Stopping isolated agent...");
                    process.close();
                } catch (IOException ex) {

                }
                // TODO
                throw new Exception(e);
            }
        }

        @Override
        public OutputMessage executeInIsolation(InputMessage message) throws Exception {
            logger.info("Calling sub-agent...");
            return gridClient.call(tokenHandle.getID(), message.getPayload(), message.getHandler(), message.getHandlerPackage(), message.getProperties(), message.getCallTimeout());
        }

        @Override
        public void close() throws Exception {
            logger.info("Stopping isolated agent...");
            process.close();
        }
    }
}
