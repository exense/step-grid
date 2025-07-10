package step.grid.agent.forker;

import ch.exense.commons.io.FileHelper;
import ch.exense.commons.processes.ExternalJVMLauncher;
import ch.exense.commons.processes.ForkedJvmBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.GridImpl;
import step.grid.TokenWrapper;
import step.grid.agent.conf.AgentForkerConfiguration;
import step.grid.client.GridClientConfiguration;
import step.grid.client.LocalGridClientImpl;
import step.grid.io.InputMessage;
import step.grid.io.OutputMessage;
import step.grid.tokenpool.Interest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class AgentForker implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(AgentForker.class);
    private final AgentForkerConfiguration configuration;
    private final GridImpl grid;
    private final String fileServerHost;
    private final Path forkedAgentConf;
    private final ExternalJVMLauncher jvmLauncher;
    private final LocalGridClientImpl gridClient;
    private static final AtomicLong nextSessionId = new AtomicLong(1);
    private final LinkedBlockingQueue<Integer> freeAgentPorts;

    public AgentForker(AgentForkerConfiguration configuration, String fileServerHost) throws IOException {
        if (!configuration.enabled) {
            throw new IllegalArgumentException("Agent forker configuration is disabled. The AgentForker is not supposed to be instantiated.");
        }

        this.configuration = configuration;
        this.fileServerHost = fileServerHost;
        forkedAgentConf = getForkedAgentConf(configuration);
        jvmLauncher = newJvmLauncher();
        grid = createGrid();
        gridClient = newClient();
        freeAgentPorts = parseAgentPortRangeAndCreateReservationTrackingQueue();
    }

    private Path getForkedAgentConf(AgentForkerConfiguration configuration) throws IOException {
        final Path forkedAgentConf;
        if (configuration.agentConf != null) {
            forkedAgentConf = Path.of(configuration.agentConf);
            logger.info("Initializing agent forker using agent conf: {}", forkedAgentConf);
        } else {
            forkedAgentConf = Files.createTempFile("ForkedAgentConf", ".yaml");
            forkedAgentConf.toFile().deleteOnExit();
            Files.write(forkedAgentConf, FileHelper.readClassLoaderResourceAsByteArray(getClass().getClassLoader(),
                    "ForkedAgentConf.yaml"), StandardOpenOption.APPEND);
            logger.info("Initializing agent forker using embedded agent conf");
        }
        if (!Files.exists(forkedAgentConf)) {
            throw new IOException("Agent forker configuration file does not exist: " + forkedAgentConf);
        }
        return forkedAgentConf;
    }

    private LinkedBlockingQueue<Integer> parseAgentPortRangeAndCreateReservationTrackingQueue() {
        int agentPortRangeStart = configuration.agentPortRangeStart;
        int agentPortRangeEnd = configuration.agentPortRangeEnd;
        if((agentPortRangeEnd > 0 && agentPortRangeStart <= 0) || (agentPortRangeEnd < agentPortRangeStart)) {
            throw new IllegalArgumentException("Agent port range start and end values are invalid.");
        }
        if (agentPortRangeStart > 0 && agentPortRangeEnd > 0) {
            logger.info("A range has been configured for the forked agents ports: [{}, {}]", agentPortRangeStart, agentPortRangeEnd);
            LinkedBlockingQueue<Integer> freeAgentPorts = new LinkedBlockingQueue<>();
            for (int i = agentPortRangeStart; i <= agentPortRangeEnd; i++) {
                freeAgentPorts.offer(i);
            }
            return freeAgentPorts;
        } else {
            return null;
        }
    }

    private GridImpl createGrid() throws IOException {
        int port = configuration.gridPort;
        logger.info("Starting agent forker grid on port {}...", port);
        File agentForkerGridFilemanager = FileHelper.createTempFolder("agentForkerGridFilemanager");
        agentForkerGridFilemanager.deleteOnExit();
        GridImpl forkedExecutionGrid = new GridImpl(agentForkerGridFilemanager, port);
        try {
            forkedExecutionGrid.start();
        } catch (Exception e) {
            throw new RuntimeException("Error while starting sub-agent grid on port " + port, e);
        }
        logger.info("Started agent forker grid on port {}", forkedExecutionGrid.getServerPort());
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
    }

    public AgentForkerSession newSession() throws Exception {
        int port;
        if (freeAgentPorts != null) {
            logger.info("Reserving agent port for forked agent from configured range...");
            port = freeAgentPorts.take();
            logger.info("Reserved agent port {}", port);
        } else {
            port = 0;
        }
        return new AgentForkerSession(port);
    }

    private ExternalJVMLauncher newJvmLauncher() {
        return new ExternalJVMLauncher(configuration.javaPath, new File("."));
    }

    private LocalGridClientImpl newClient() {
        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        gridClientConfiguration.setNoMatchExistsTimeout(configuration.startTimeoutMs);
        return new LocalGridClientImpl(gridClientConfiguration, grid);
    }

    public class AgentForkerSession implements AutoCloseable {

        final Process process;
        private final TokenWrapper tokenHandle;
        private final ForkedJvmBuilder forkedJvmBuilder;
        private final long id;
        private final Path tempDirectory;
        private final int port;

        public AgentForkerSession(int port) throws Exception {
            this.port = port;
            id = nextSessionId.getAndIncrement();
            tempDirectory = Files.createTempDirectory("forked-agent" + id);
            logger.info("Starting forked agent {} in {}...", id, tempDirectory);
            forkedJvmBuilder = new ForkedJvmBuilder(getJavaPath(), findMainClass(), getVmArgs(), getProgArgs());
            process = new ProcessBuilder(forkedJvmBuilder.getProcessCommand())
                    .directory(tempDirectory.toFile()).redirectErrorStream(true).start();

            CompletableFuture.runAsync(()  -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("Forked agent {} output -- {}", id, line);
                    }
                } catch (Exception e) {
                    logger.error("Error reading output of forked agent {}", id, e);
                }
            });

            try {
                logger.info("Waiting for forked agent {} to connect...", id);
                tokenHandle = gridClient.getTokenHandle(Map.of(), Map.of("forkedAgentId", new Interest(Pattern.compile("^" + id + "$"), true)), true);
            } catch (Throwable e) {
                String message;
                if (e.getMessage().contains("Not able to find any agent token")) {
                    message = "Timeout while waiting for the forked agent {} to start and join the forked agent grid";
                } else {
                    message = "Unexpected error while starting the forked agent {}";
                }
                logger.error(message, id, e);
                close();
                throw new Exception(message, e);
            }
        }

        private List<String> getProgArgs() {


            return List.of("-config=" + forkedAgentConf.toAbsolutePath(), "-gridHost=http://localhost:" + grid.getServerPort(), "-fileServerHost=" + fileServerHost, "-forkedAgentId=" + id);
        }

        private String getJavaPath() {
            return Optional.ofNullable(configuration.javaPath).orElse(ProcessHandle.current().info().command().orElseThrow(() ->
                    new IllegalArgumentException("The javaPath is not set and the path to the java executable of the current process could not be determined.")));
        }

        private List<String> getVmArgs() {
            List<String> vmArgs;
            if(configuration.vmArgs != null && !configuration.vmArgs.isEmpty()) {
                vmArgs = Arrays.asList(configuration.vmArgs.split(" "));
            } else {
                vmArgs = List.of();
            }
            return vmArgs;
        }

        public OutputMessage delegateExecution(InputMessage message) throws Exception {
            logger.info("Calling forked agent {}...", id);
            return gridClient.call(tokenHandle.getID(), message.getPayload(), message.getHandler(), message.getHandlerPackage(), message.getProperties(), message.getCallTimeout());
        }

        /**
         * This method tries to determine the main class of the current process. For this it uses the system property 'sun.java.command'
         * which in most JVM implementations contains the start command of the process
         *
         * @return the main class of the current process
         */
        private String findMainClass() {
            String command = Objects.requireNonNull(System.getProperty("sun.java.command"),
                    "Unable to determine the main class to use for the forked agent. The property 'sun.java.command' is not set");
            if (logger.isDebugEnabled()) {
                logger.debug("Using the system property 'sun.java.command' to determine the main class of the current process: {}", command);
            }
            return command.split(" ")[0];
        }

        @Override
        public void close() {
            logger.info("Stopping forked agent {}...", id);
            process.destroy();
            try {
                boolean terminated = process.waitFor(configuration.shutdownTimeout, TimeUnit.SECONDS);
                if(!terminated) {
                    logger.warn("Timeout while waiting for the forked agent {} to shut down gracefully. Destroying it forcibly.", id);
                    process.destroyForcibly();
                    terminated = process.waitFor(configuration.shutdownTimeout, TimeUnit.SECONDS);
                    if(!terminated) {
                        logger.error("Timeout while waiting for the forked agent {} to stop after destroying it forcibly.", id);
                    } else {
                        logger.info("Successfully stopped forked agent forcibly {}.", id);
                    }
                } else {
                    logger.info("Successfully stopped forked agent {}.", id);
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for the forked agent {} to stop.", id, e);
            }

            logger.info("Deleting forked agent execution directory {}...", tempDirectory);
            tempDirectory.toFile().delete();

            forkedJvmBuilder.close();

            if (port != 0) {
                logger.info("Releasing agent port {}...", port);
                freeAgentPorts.offer(port);
            }
        }
    }
}
