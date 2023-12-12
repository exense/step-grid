/*******************************************************************************
 * Copyright (C) 2020, exense GmbH
 *  
 * This file is part of STEP
 *  
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package step.grid.agent;

import ch.exense.commons.app.ArgumentParser;

import org.eclipse.jetty.server.*;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.Token;
import step.grid.agent.conf.AgentConf;
import step.grid.agent.conf.TokenConf;
import step.grid.agent.conf.TokenGroupConf;
import step.grid.agent.tokenpool.AgentTokenPool;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.app.configuration.ConfigurationParser;
import step.grid.app.server.BaseServer;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerClientImpl;
import step.grid.filemanager.FileManagerConfiguration;
import step.grid.tokenpool.Interest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class Agent extends BaseServer implements AutoCloseable {
	
	private static final Logger logger = LoggerFactory.getLogger(Agent.class);

	private static final String TOKEN_ID = "$tokenid";
	private static final String AGENT_ID = "$agentid";

	private final String id = UUID.randomUUID().toString();
	private final AgentTokenPool tokenPool = new AgentTokenPool();
	
	private final Server server;
	private final Timer timer;
	private final RegistrationTask registrationTask;
	private final AgentTokenServices agentTokenServices;
	
	private final String agentUrl;
	private final long gracefulShutdownTimeout;
	private final RegistrationClient registrationClient;
	private final FileManagerClient fileManagerClient;
	private volatile boolean stopped = false;
	private volatile boolean registered = false;

	public static void main(String[] args) throws Exception {
		newInstanceFromArgs(args);
	}
	
	public static Agent newInstanceFromArgs(String[] args) throws Exception {
		ArgumentParser arguments = new ArgumentParser(args);

		String agentConfStr = arguments.getOption("config");
		
		if(agentConfStr!=null) {
			ConfigurationParser<AgentConf> parser = new ConfigurationParser<AgentConf>();
			AgentConf agentConf = parser.parse(arguments, new File(agentConfStr), AgentConf.class);

			if(arguments.hasOption("gridHost")) {
				agentConf.setGridHost(arguments.getOption("gridHost"));
			}

			if(arguments.hasOption("fileServerHost")) {
				agentConf.setFileServerHost(arguments.getOption("fileServerHost"));
			}
			
			if(arguments.hasOption("agentPort")) {
				agentConf.setAgentPort(Integer.decode(arguments.getOption("agentPort")));
			}
			
			if(arguments.hasOption("agentHost")) {
				agentConf.setAgentHost(arguments.getOption("agentHost"));
			}
			
			if(arguments.hasOption("agentUrl")) {
				agentConf.setAgentUrl(arguments.getOption("agentUrl"));
			}
			
			return new Agent(agentConf);
		} else {
			throw new RuntimeException("Argument '-config' is missing.");
		}
	}
	
	public Agent(AgentConf agentConf) throws Exception {
		super();

		validateConfiguration(agentConf);

		String agentHost = agentConf.getAgentHost();
		String agentUrl = agentConf.getAgentUrl();
		Integer agentPort = agentConf.getAgentPort();
		Long agentConfGracefulShutdownTimeout = agentConf.getGracefulShutdownTimeout();
		gracefulShutdownTimeout = agentConfGracefulShutdownTimeout != null ? agentConfGracefulShutdownTimeout : 30000;

		String gridUrl = agentConf.getGridHost();
		String fileServerHost = Optional.ofNullable(agentConf.getFileServerHost()).orElse(gridUrl);

		registrationClient = new RegistrationClient(gridUrl, fileServerHost,
				agentConf.getGridConnectTimeout(), agentConf.getGridReadTimeout());


		fileManagerClient = initFileManager(registrationClient, agentConf.getWorkingDir(), agentConf.getFileManagerConfiguration());

		agentTokenServices = new AgentTokenServices(fileManagerClient);
		agentTokenServices.setAgentProperties(agentConf.getProperties());
		agentTokenServices.setApplicationContextBuilder(new ApplicationContextBuilder());

		buildTokenList(agentConf);

		int serverPort = this.resolveServerPort(agentUrl, agentPort);

		logger.info("Starting server...");
		server = startServer(agentConf, serverPort);

		int actualServerPort = this.getActualServerPort(server);
		logger.info("Successfully started server on port " + actualServerPort);

		this.agentUrl = this.getOrBuildActualUrl(agentHost, agentUrl, actualServerPort, agentConf.isSsl());

		logger.info("Starting grid registration task using grid URL " + gridUrl + "...");
		registrationTask = createGridRegistrationTask(registrationClient);
		timer = createGridRegistrationTimerAndRegisterTask(agentConf);

		logger.info("Agent successfully started on port " + actualServerPort
				+ ". The agent will publish following URL for incoming connections: " + this.agentUrl);
	}

	private RegistrationTask createGridRegistrationTask(RegistrationClient registrationClient) {
		return new RegistrationTask(this, registrationClient);
	}

	private Timer createGridRegistrationTimerAndRegisterTask(AgentConf agentConf) {
		Timer timer = new Timer();
		timer.schedule(registrationTask, agentConf.getRegistrationOffset(), agentConf.getRegistrationPeriod());
		return timer;
	}

	private void buildTokenList(AgentConf agentConf) {
		List<TokenGroupConf> tokenGroups = agentConf.getTokenGroups();
		if(tokenGroups!=null) {
			for(TokenGroupConf group:tokenGroups) {
				TokenConf tokenConf = group.getTokenConf();
				if(tokenConf != null) {
					addTokens(group.getCapacity(), tokenConf.getAttributes(), tokenConf.getSelectionPatterns(), 
							tokenConf.getProperties());
				} else {
					throw new IllegalArgumentException("Missing section 'tokenConf' in agent configuration");
				}
			}
		}
	}

	public boolean isRunning() {
		return server.isRunning();
	}

	private Server startServer(AgentConf agentConf, int port) throws Exception {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.packages(AgentServices.class.getPackage().getName());
		resourceConfig.register(ObjectMapperResolver.class);
		final Agent agent = this;
		resourceConfig.register(new AbstractBinder() {
			@Override
			protected void configure() {
				bind(agent).to(Agent.class);
			}
		});

		return this.startServer(agentConf, port, resourceConfig);
	}

	private void validateConfiguration(AgentConf agentConf) {
		assertMandatoryOption(agentConf.getGridHost(), "gridHost");
		if (agentConf.isSsl()) {
			assertMandatorySslOption(agentConf.getKeyStorePath(), "keyStorePath");
			assertMandatorySslOption(agentConf.getKeyStorePassword(), "keyStorePassword");
			assertMandatorySslOption(agentConf.getKeyManagerPassword(), "keyManagerPassword");
		}
	}

	private void assertMandatoryOption(String actualOptionValue, String optionName) {
		assertOption(actualOptionValue, "Missing option '" + optionName + "'. This option is mandatory.");
	}

	private void assertMandatorySslOption(String actualOptionValue, String optionName) {
		assertOption(actualOptionValue,
				"Missing option '" + optionName + "'. This option is mandatory when SSL is enabled.");
	}

	private void assertOption(String actualOptionValue, String errorMessage) {
		if (actualOptionValue == null || actualOptionValue.trim().length() == 0) {
			throw new IllegalArgumentException(errorMessage);
		}
	}
	
	public String getId() {
		return id;
	}
	
	public void addTokens(int count, Map<String, String> attributes, Map<String, String> selectionPatterns, Map<String, String> properties) {
		for(int i=0;i<count;i++) {
			AgentTokenWrapper token = new AgentTokenWrapper();
			token.getToken().setAgentid(id);
			Map<String, String> allAttributes = new HashMap<>();
			if(attributes != null) {
				allAttributes.putAll(attributes);
			}
			allAttributes.put(AgentTypes.AGENT_TYPE_KEY, AgentTypes.AGENT_TYPE);
			allAttributes.put(AGENT_ID, id);
			allAttributes.put(TOKEN_ID, token.getUid());
			token.setAttributes(allAttributes);
			token.setSelectionPatterns(createInterestMap(selectionPatterns));
			token.setProperties(properties);
			token.setServices(agentTokenServices);
			tokenPool.offerToken(token);
		}
	}
	
	private Map<String, Interest> createInterestMap(Map<String, String> selectionPatterns) {
		HashMap<String, Interest> result = new HashMap<String, Interest>();
		if(selectionPatterns!=null) {
			for(Entry<String, String> entry:selectionPatterns.entrySet()) {
				Interest i = new Interest(Pattern.compile(entry.getValue()), true);
				result.put(entry.getKey(), i);
			}
		}
		return result;
	}

	private FileManagerClient initFileManager(RegistrationClient registrationClient, String workingDir, FileManagerConfiguration fileManagerConfig) throws IOException {
		String fileManagerDirPath;
		if(workingDir!=null) {
			fileManagerDirPath = workingDir;
		} else {
			fileManagerDirPath = ".";
		}
		fileManagerDirPath+="/filemanager";
		File fileManagerDir = new File(fileManagerDirPath);
		if(!fileManagerDir.exists()) {
			Files.createDirectories(fileManagerDir.toPath());
		}
		
		FileManagerClient fileManagerClient = new FileManagerClientImpl(fileManagerDir, registrationClient, fileManagerConfig);
		return fileManagerClient;
	}

	protected String getAgentUrl() {
		return agentUrl;
	}

	protected AgentTokenPool getTokenPool() {
		return tokenPool;
	}

	public AgentTokenServices getAgentTokenServices() {
		return agentTokenServices;
	}

	protected List<Token> getTokens() {
		List<Token> tokens = new ArrayList<>();
		for(AgentTokenWrapper wrapper:tokenPool.getTokens()) {
			tokens.add(wrapper.getToken());
		}
		return tokens;
	}


	public synchronized void preStop() throws Exception {
		if(!stopped) {
			logger.info("Shutting down...");

			// Stopping registration task
			if (timer != null) {
				timer.cancel();
			}

			if (registrationTask != null) {
				registrationTask.cancel();
				registrationTask.unregister();
				registrationTask.destroy();
			}

			logger.info("Waiting for tokens to be released...");

			// Wait until all tokens are released
			boolean gracefullyStopped = pollUntil(tokenPool::areAllTokensFree, gracefulShutdownTimeout);

			if (gracefullyStopped) {
				logger.info("Agent gracefully stopped");
			} else {
				logger.warn("Timeout while waiting for all tokens to be released. Agent forcibly stopped");
			}

			//client is shared between registration tasks and file manager, closing it only once all token are released
			if (registrationClient != null) {
				registrationClient.close();
			}
			if (fileManagerClient != null) {
				fileManagerClient.close();
			}
		}
	}

	@Override
	public synchronized void close() throws Exception {
		if(!stopped) {
			preStop();

			// Stopping HTTP server
			server.stop();
			logger.info("Web server stopped");

			stopped = true;
		}
	}

	public boolean isRegistered() {
		return registered;
	}

	public void setRegistered(boolean registered) {
		this.registered = registered;
	}
	
	private static boolean pollUntil(Supplier<Boolean> predicate, long timeout) throws InterruptedException {
		long t1 = System.currentTimeMillis();
		while (System.currentTimeMillis() < t1 + (timeout)) {
			if(predicate.get()) {
				return true;
			}
			Thread.sleep(100);
		}
		return false;
	}
}
