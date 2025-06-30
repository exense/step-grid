package step.grid.agent.isolation;

import ch.exense.commons.io.FileHelper;
import ch.exense.commons.processes.ExternalJVMLauncher;
import ch.exense.commons.processes.ManagedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.GridImpl;
import step.grid.TokenWrapper;
import step.grid.agent.Agent;
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

public class IsolationManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(IsolationManager.class);
    private final IsolatedExecutionConfiguration configuration;
    private final GridImpl grid;
    private final String fileServerHost;
    private final Path isolatedAgentConf;
    private ExternalJVMLauncher jvmLauncher;
    private LocalGridClientImpl gridClient;

    public IsolationManager(IsolatedExecutionConfiguration configuration, String fileServerHost) throws IOException {
        this.configuration = configuration;
        this.fileServerHost = fileServerHost;

        isolatedAgentConf = Files.createTempFile("IsolatedAgentConf", ".yaml");
        Files.write(isolatedAgentConf, FileHelper.readResourceAsByteArray(IsolatedSession.class, "IsolatedAgentConf.yaml"), new OpenOption[]{StandardOpenOption.APPEND});

        if (configuration.enabled) {
            this.jvmLauncher = newJvmLauncher();
            this.grid = createGrid(0);
            this.gridClient = newClient();
        } else {
            this.grid = null;
        }
    }

    private static GridImpl createGrid(int port) {
        GridImpl forkedExecutionGrid = new GridImpl(new File("./filemanager"), port);
        try {
            forkedExecutionGrid.start();
        } catch (Exception e) {
            throw new RuntimeException("Error while starting sub-agent grid on port " + port, e);
        }
        return forkedExecutionGrid;
    }

    @Override
    public void close() throws Exception {
        if (grid != null) {
            grid.stop();
        }
        if (gridClient != null) {
            gridClient.close();
        }
        isolatedAgentConf.toFile().delete();
    }

    public class IsolatedSession implements AutoCloseable {

        final ManagedProcess process;
        private final TokenWrapper tokenHandle;
        private final String id;

        public IsolatedSession() throws Exception {
            id = UUID.randomUUID().toString();
            logger.info("Creating isolated session...");
            logger.info("Starting isolated agent in a separated JVM...");
            this.process = jvmLauncher.launchExternalJVM("IsolatedExecutionAgent", Agent.class, List.of(), List.of("-config=" + isolatedAgentConf.toAbsolutePath(), "-gridHost=http://localhost:" + grid.getServerPort(), "-fileServerHost=" + fileServerHost, "-isolatedSessionId=" + id));
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
                throw new Exception();
            }
        }

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

    public IsolatedSession newSession() throws Exception {
        return new IsolatedSession();
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

    public boolean isEnabled() {
        return configuration.enabled;
    }
}
