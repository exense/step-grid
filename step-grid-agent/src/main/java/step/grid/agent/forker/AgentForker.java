package step.grid.agent.forker;

import ch.exense.commons.io.FileHelper;
import ch.exense.commons.processes.ForkedJvmBuilder;
import ch.exense.commons.resilience.RetryHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.AgentRef;
import step.grid.GridImpl;
import step.grid.TokenWrapper;
import step.grid.agent.Agent;
import step.grid.agent.conf.AgentForkerConfiguration;
import step.grid.client.AbstractGridClientImpl;
import step.grid.client.GridClientConfiguration;
import step.grid.client.GridClientException;
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

import static step.grid.agent.Agent.PROPERTY_PREFIX;

public class AgentForker implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(AgentForker.class);
    private final AgentForkerConfiguration configuration;
    private final GridImpl grid;
    private final String fileServerHost;
    private final Path forkedAgentConf;
    private final LocalGridClientImpl gridClient;
    private static final AtomicLong nextSessionId = new AtomicLong(1);
    private final LinkedBlockingQueue<Integer> freeAgentPorts;
    private final Path logbackConfiguration;
    private final Path workingDirectory;

    public AgentForker(AgentForkerConfiguration configuration, String fileServerHost) throws IOException {
        if (!configuration.enabled) {
            throw new IllegalArgumentException("Agent forker configuration is disabled. The AgentForker is not supposed to be instantiated.");
        }
        this.configuration = configuration;
        this.fileServerHost = fileServerHost;
        workingDirectory = Path.of(Objects.requireNonNull(configuration.workingDirectory)).toAbsolutePath();
        workingDirectory.toFile().mkdirs();
        logger.debug("Using working directory: {}", workingDirectory);
        forkedAgentConf = getForkedAgentConf(configuration);
        logbackConfiguration = getLogbackConfiguration(configuration);
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
            logger.info("Extracting embedded agent conf to {}...", workingDirectory);
            forkedAgentConf = Files.createTempFile(workingDirectory, "ForkedAgentConf", ".yaml");
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

    private Path getLogbackConfiguration(AgentForkerConfiguration configuration) throws IOException {
        final Path logbackConfiguration;
        if (configuration.logbackConf != null) {
            logbackConfiguration = Path.of(configuration.logbackConf);
            if (!Files.exists(logbackConfiguration)) {
                throw new IOException("Logback configuration file does not exist: " + logbackConfiguration);
            } else {
                logger.info("Using logback configuration: {}", logbackConfiguration);
            }
        } else {
            logger.info("Extracting embedded logback conf to {}...", workingDirectory);
            logbackConfiguration = Files.createTempFile(workingDirectory, "logback-forked-agent", ".xml");
            logbackConfiguration.toFile().deleteOnExit();
            Files.write(logbackConfiguration, FileHelper.readClassLoaderResourceAsByteArray(getClass().getClassLoader(),
                    "logback-forked-agent.xml"), StandardOpenOption.APPEND);
            logger.info("Using embedded logback configuration");
        }
        return logbackConfiguration;
    }

    private LinkedBlockingQueue<Integer> parseAgentPortRangeAndCreateReservationTrackingQueue() {
        int agentPortRangeStart = configuration.agentPortRangeStart;
        int agentPortRangeEnd = configuration.agentPortRangeEnd;
        if ((agentPortRangeEnd > 0 && agentPortRangeStart <= 0) || (agentPortRangeEnd < agentPortRangeStart)) {
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

    /**
     * Starts a dedicated forked agent process
     *
     * @param createSession   if a session should be created in the forked agent when selecting its token
     * @param agentProperties the full map of agent properties that should be used to start the forked agent
     * @return the associated {@link ForkedAgent} to interact with the forked agent
     * @throws Exception if any error occurs while creating the forked agent
     */
    public ForkedAgent startForkedAgent(boolean createSession, Map<String, String> agentProperties) throws Exception {
        int port;
        if (freeAgentPorts != null) {
            logger.info("Reserving agent port for forked agent from configured range...");
            port = freeAgentPorts.take();
            logger.info("Reserved agent port {}", port);
        } else {
            port = 0;
        }
        return new ForkedAgent(port, createSession, agentProperties);
    }

    private LocalGridClientImpl newClient() {
        GridClientConfiguration gridClientConfiguration = new GridClientConfiguration();
        gridClientConfiguration.setNoMatchExistsTimeout(configuration.startTimeoutMs);
        return new LocalGridClientImpl(gridClientConfiguration, grid);
    }

    public class ForkedAgent implements AutoCloseable {

        private final Process process;
        private final TokenWrapper tokenHandle;
        private final ForkedJvmBuilder forkedJvmBuilder;
        private final long id;
        private final Path tempDirectory;
        private final int port;
        private final Map<String, String> agentProperties;
        private final AgentRef agentRef;

        public ForkedAgent(int port, boolean createSession, Map<String, String> agentProperties) throws Exception {
            this.port = port;
            this.agentProperties = agentProperties;
            id = nextSessionId.getAndIncrement();
            tempDirectory = Files.createTempDirectory(workingDirectory, "forked-agent" + id);
            logger.info("Starting forked agent {} in {}...", id, tempDirectory);
            forkedJvmBuilder = new ForkedJvmBuilder(getJavaPath(), findMainClass(), getVmArgs(), getProgArgs());
            try {
                process = new ProcessBuilder(forkedJvmBuilder.getProcessCommand())
                        .directory(tempDirectory.toFile()).redirectErrorStream(true).start();
            } catch (Exception e) {
                logger.error("Error while starting forked agent {}", id, e);
                close();
                throw new Exception("Error while starting forked agent", e);
            }

            CompletableFuture.runAsync(() -> {
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
                tokenHandle = gridClient.getTokenHandle(Map.of(), Map.of("forkedAgentId", new Interest(Pattern.compile("^" + id + "$"), true)), createSession);
                agentRef = tokenHandle.getAgent();
            } catch (Throwable e) {
                String message;
                if (e.getMessage().contains("Not able to find any agent token")) {
                    message = "Timeout while waiting for the forked agent {} to start and join the forked agent grid";
                } else {
                    message = "Unexpected error while starting the forked agent {}";
                }
                logger.error(message, id, e);
                close();
                throw new Exception(message.replace(" {}", ""), e);
            }
        }

        private List<String> getProgArgs() {
            ArrayList<String> propArgs = new ArrayList<>();
            if (agentProperties != null) {
                agentProperties.forEach((key, value) -> propArgs.add("-" + PROPERTY_PREFIX + key + "=" + value));
            }
            propArgs.addAll(List.of("-config=" + forkedAgentConf.toAbsolutePath(), "-gridHost=http://localhost:" + grid.getServerPort(), "-fileServerHost=" + fileServerHost, "-forkedAgentId=" + id));
            return propArgs;
        }

        private String getJavaPath() {
            return Optional.ofNullable(configuration.javaPath).orElse(ProcessHandle.current().info().command().orElseThrow(() ->
                    new IllegalArgumentException("The javaPath is not set and the path to the java executable of the current process could not be determined.")));
        }

        private List<String> getVmArgs() {
            List<String> vmArgs = new ArrayList<>();
            vmArgs.add("-Dlogback.configurationFile=" + logbackConfiguration.toFile().getAbsolutePath());
            if (configuration.vmArgs != null && !configuration.vmArgs.isEmpty()) {
                vmArgs.addAll(Arrays.asList(configuration.vmArgs.split(" ")));
            }
            return vmArgs;
        }

        public OutputMessage delegateExecution(InputMessage message) throws Exception {
            // Subtract an offset from the initial call timeout to ensure that the forked agent's timeout
            // triggers first. This allows the forked agent to handle the timeout gracefully and
            // terminate or clean up before the call to the forked agent times out.
            int callTimeout = message.getCallTimeout() - configuration.callTimeoutOffsetMs;
            if (callTimeout <= 0) {
                throw new IllegalArgumentException("The defined call timeout is too low. It must be greater than the required offset of " + configuration.callTimeoutOffsetMs + "ms.");
            }

            logger.info("Calling forked agent {}...", id);
            return gridClient.call(tokenHandle.getID(), message.getPayload(), message.getHandler(), message.getHandlerPackage(), message.getProperties(), callTimeout);
        }

        public void interruptExecution() {
            logger.info("Interrupting execution on forked agent {}...", id);
            try {
                gridClient.interruptTokenExecution(tokenHandle.getID());
            } catch (GridClientException | AbstractGridClientImpl.AgentCommunicationException e) {
                logger.warn("Unexpected error while interrupting execution on forked agent {}", id, e);
                throw new RuntimeException(e);
            }
        }

        /**
         * This method tries to determine the main class of the current process, which can be different from {@link Agent}.
         * For this it uses the system property 'sun.java.command'
         * which in most JVM implementations contains the start command of the process.
         *
         * @return the main class of the current process
         */
        private String findMainClass() {
            String command = Objects.requireNonNull(System.getProperty("sun.java.command"),
                    "Unable to determine the main class to use for the forked agent. The property 'sun.java.command' is not set");
            if (logger.isDebugEnabled()) {
                logger.debug("Using the system property 'sun.java.command' to determine the main class of the current process: {}", command);
            }
            String command0 = command.split(" ")[0];
            // When running agent tests in JUnit for instance, the main class is a JUnit specific class.
            // In this case we fall back to Agent.class
            if (!command0.contains("Agent")) {
                logger.warn("The main class of the current process is {} and doesn't seem to be an Agent main class. Falling back to Agent.class", command0);
                command0 = Agent.class.getName();
            }
            return command0;
        }

        @Override
        public void close() {
            if(tokenHandle != null) {
                // Release the token in order for the session and its objects to be released in the forked agent
                logger.info("Releasing token of forked agent {}...", id);
                try {
                    gridClient.returnTokenHandle(tokenHandle.getID());
                } catch (Exception e) {
                    logger.error("Error returning token handle for forked agent {}", id, e);
                }
            }
            if(process != null) {
                if (agentRef != null) {
                    logger.info("Stopping forked agent {} gracefully...", id);
                    try {
                        gridClient.shutdownAgent(agentRef);
                    } catch (AbstractGridClientImpl.AgentCommunicationException e) {
                        logger.error("Error while shutting down forked agent {}", id, e);
                    }
                }

                try {
                    boolean terminated = process.waitFor(configuration.shutdownTimeoutMs, TimeUnit.MILLISECONDS);
                    if (!terminated) {
                        logger.warn("Timeout while waiting for the forked agent {} to shut down gracefully. Destroying it forcibly.", id);
                        process.destroyForcibly();
                        terminated = process.waitFor(configuration.shutdownTimeoutMs, TimeUnit.MILLISECONDS);
                        if (!terminated) {
                            logger.error("Timeout while waiting for the forked agent {} to stop after destroying it forcibly.", id);
                        } else {
                            logger.info("Successfully stopped forked agent forcibly {}.", id);
                        }
                    } else {
                        logger.info("Successfully stopped forked agent {} gracefully.", id);
                    }
                } catch (InterruptedException e) {
                    logger.error("Interrupted while waiting for the forked agent {} to stop.", id, e);
                }
            }

            deleteTempDir();

            forkedJvmBuilder.close();

            if (port != 0) {
                logger.info("Releasing agent port {}...", port);
                freeAgentPorts.offer(port);
            }
        }

        private void deleteTempDir() {
            logger.info("Deleting forked agent execution directory {}...", tempDirectory);
            try {
                RetryHelper.executeWithRetryOnExceptions(
                        () -> {
                            FileUtils.deleteDirectory(tempDirectory.toFile());
                            logger.info("Deleted forked agent execution directory {}.", tempDirectory);
                            return null;
                        },
                        5,  // maxRetries
                        configuration.tempDirectoryDeletionRetryWait,
                        List.of(IOException.class),
                        "Delete forked agent execution directory " + tempDirectory
                );
            } catch (Exception e) {
                logger.warn("Failed to delete forked agent execution directory {} after retries.", tempDirectory, e);
            }
        }
    }
}
