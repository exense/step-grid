package step.grid.agent.conf;

public class AgentForkerConfiguration {
    /**
     * Defines if the agent forking is enabled.
     */
    public boolean enabled;
    /**
     * Specifies the path to the Java executable used to start the forked agent.
     * If not set, the Java executable of the main agent will be used by default.
     */
    public String javaPath;
    /**
     * Optional JVM arguments to be applied when starting the forked agent.
     * These arguments are appended to the command used to launch the agent.
     */
    public String vmArgs;
    /**
     * Path to a custom configuration file for the forked agent.
     * This file allows overriding default configuration values.
     */
    public String agentConf;
    /**
     * Path to a custom Logback configuration file to be used by the forked agent.
     * Useful for adjusting logging behavior of the forked agent.
     */
    public String logbackConf;
    /**
     * Timeout in milliseconds to wait for the forked agent to fully start.
     * If the agent doesn't start within this time, startup is considered to have failed.
     */
    public long startTimeoutMs = 20_000;
    /**
     * Timeout in milliseconds to allow for a graceful shutdown of the forked agent.
     * If the shutdown is not completed within this time, the agent is forcibly terminated.
     */
    public long shutdownTimeoutMs = 10_000;
    /**
     * When delegating calls to the forked agent, this offset in milliseconds is subtracted
     * from the overall call timeout to ensure the forked agent has sufficient time
     * to process and handle the timeout internally.
     */
    public int callTimeoutOffsetMs = 1000;
    /**
     * The port number on which the embedded grid service will be started inside the main agent.
     * Forked agents will join this grid. A value of 0 indicates that a random available port will be used.
     */
    public int gridPort = 0;
    /**
     * The starting port of the range used by the forked agent to expose its services.
     * This is the first port in the inclusive range the agent may bind to.
     * By default, a random available port will be used.
     */
    public int agentPortRangeStart = 0;
    /**
     * The ending port of the range used by the forked agent to expose its services.
     * This is the last port in the inclusive range the agent may bind to.
     * By default, a random available port will be used.
     */
    public int agentPortRangeEnd = 0;
    /**
     * When a forked agent is shut down, its temporary directory is deleted.
     * If immediate deletion fails, the system retries up to five times.
     * This value defines the wait time in milliseconds between each retry attempt.
     */
    public long tempDirectoryDeletionRetryWait = 100;
    /**
     * Path to the working directory where the execution directories of the
     * forked agent are created.
     */
    public String workingDirectory = "work/forked-agents";
}
