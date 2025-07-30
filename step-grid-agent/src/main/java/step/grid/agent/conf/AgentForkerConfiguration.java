package step.grid.agent.conf;

public class AgentForkerConfiguration {
    public boolean enabled;
    public String javaPath;
    public String vmArgs;
    public String agentConf;
    public String logbackConf;
    public long startTimeoutMs = 20_000;
    public long shutdownTimeoutMs = 10_000;
    public int callTimeoutOffsetMs = 1000;
    public int gridPort = 0;
    public int agentPortRangeStart = 0;
    public int agentPortRangeEnd = 0;
}
