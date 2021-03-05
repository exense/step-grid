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

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import ch.exense.commons.app.ArgumentParser;
import step.grid.Token;
import step.grid.agent.conf.AgentConf;
import step.grid.agent.conf.AgentConfParser;
import step.grid.agent.conf.TokenConf;
import step.grid.agent.conf.TokenGroupConf;
import step.grid.agent.tokenpool.AgentTokenPool;
import step.grid.agent.tokenpool.AgentTokenWrapper;
import step.grid.contextbuilder.ApplicationContextBuilder;
import step.grid.filemanager.FileManagerClient;
import step.grid.filemanager.FileManagerClientImpl;
import step.grid.tokenpool.Interest;

public class Agent implements AutoCloseable {
	
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

	public static Agent newInstanceFromArgs(String[] args) throws Exception {
		ArgumentParser arguments = new ArgumentParser(args);

		String agentConfStr = arguments.getOption("config");
		
		if(agentConfStr!=null) {
			AgentConfParser parser = new AgentConfParser();
			AgentConf agentConf = parser.parser(arguments, new File(agentConfStr));

			if(arguments.hasOption("gridHost")) {
				agentConf.setGridHost(arguments.getOption("gridHost"));
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
		boolean ssl = agentConf.isSsl();

		String gridUrl = agentConf.getGridHost();
		RegistrationClient registrationClient = new RegistrationClient(gridUrl,
				agentConf.getGridConnectTimeout(), agentConf.getGridReadTimeout());

		FileManagerClient fileManagerClient = initFileManager(registrationClient, agentConf.getWorkingDir());

		agentTokenServices = new AgentTokenServices(fileManagerClient);
		agentTokenServices.setAgentProperties(agentConf.getProperties());
		agentTokenServices.setApplicationContextBuilder(new ApplicationContextBuilder());

		buildTokenList(agentConf);

		int serverPort = getServerPort(agentUrl, agentPort);

		logger.info("Starting server...");
		server = startServer(agentConf, serverPort, ssl);

		int actualServerPort = getActualServerPort();
		logger.info("Successfully started server on port " + actualServerPort);

		this.agentUrl = getOrBuildActualAgentUrl(agentHost, agentUrl, actualServerPort, ssl);

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
		timer.schedule(registrationTask, 0, agentConf.getRegistrationPeriod());
		return timer;
	}

	private void buildTokenList(AgentConf agentConf) {
		List<TokenGroupConf> tokenGroups = agentConf.getTokenGroups();
		if(tokenGroups!=null) {
			for(TokenGroupConf group:tokenGroups) {
				TokenConf tokenConf = group.getTokenConf();
				addTokens(group.getCapacity(), tokenConf.getAttributes(), tokenConf.getSelectionPatterns(), 
						tokenConf.getProperties());
			}
		}
	}

	private int getServerPort(String agentUrl, Integer agentPort) throws MalformedURLException {
		int port;
		if (agentPort != null) {
			port = agentPort;
		} else {
			if (agentUrl != null) {
				URL url = new URL(agentUrl);
				int urlPort = url.getPort();
				port = urlPort != -1 ? urlPort : url.getDefaultPort();
			} else {
				port = 0;
			}
		}
		return port;
	}

	private String getOrBuildActualAgentUrl(String agentHost, String agentUrl, int localPort, boolean ssl)
			throws UnknownHostException {
		String actualAgentUrl;
		if (agentUrl == null) {
			// agentUrl not set. generate it
			String scheme;
			if (ssl) {
				scheme = "https://";
			} else {
				scheme = "http://";
			}

			String host;
			if (agentHost == null) {
				// agentHost not specified. Calculate it
				host = Inet4Address.getLocalHost().getCanonicalHostName();
			} else {
				host = agentHost;
			}
			actualAgentUrl = scheme + host + ":" + localPort;
		} else {
			actualAgentUrl = agentUrl;
		}
		return actualAgentUrl;
	}

	private int getActualServerPort() {
		return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
	}

	private Server startServer(AgentConf agentConf, int port, boolean ssl) throws Exception {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.packages(AgentServices.class.getPackage().getName());
		resourceConfig.register(JacksonJsonProvider.class);
		resourceConfig.register(ObjectMapperResolver.class);
		final Agent agent = this;
		resourceConfig.register(new AbstractBinder() {
			@Override
			protected void configure() {
				bind(agent).to(Agent.class);
			}
		});
		ServletContainer servletContainer = new ServletContainer(resourceConfig);

		ServletHolder sh = new ServletHolder(servletContainer);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(sh, "/*");

		Server server = new Server();

		ServerConnector connector;
		if (ssl) {
			String keyStorePath = agentConf.getKeyStorePath();
			String keyStorePassword = agentConf.getKeyStorePassword();
			String keyManagerPassword = agentConf.getKeyManagerPassword();

			HttpConfiguration https = new HttpConfiguration();
			https.addCustomizer(new SecureRequestCustomizer());

			SslContextFactory sslContextFactory = new SslContextFactory();
			sslContextFactory.setKeyStorePath(keyStorePath);
			sslContextFactory.setKeyStorePassword(keyStorePassword);
			sslContextFactory.setKeyManagerPassword(keyManagerPassword);

			connector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"),
					new HttpConnectionFactory(https));
		} else {
			HttpConfiguration http = new HttpConfiguration();
			http.addCustomizer(new SecureRequestCustomizer());

			connector = new ServerConnector(server);
			connector.addConnectionFactory(new HttpConnectionFactory(http));
			server.addConnector(connector);
		}
		connector.setPort(port);
		server.addConnector(connector);

		ContextHandlerCollection contexts = new ContextHandlerCollection();
		contexts.setHandlers(new Handler[] { context });
		server.setHandler(contexts);

		server.start();

		return server;
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
			allAttributes.putAll(attributes);
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

	private FileManagerClient initFileManager(RegistrationClient registrationClient, String workingDir) throws IOException {
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
		
		FileManagerClient fileManagerClient = new FileManagerClientImpl(fileManagerDir, registrationClient);
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

	@Override
	public void close() throws Exception {
		if(timer!=null) {
			timer.cancel();
		}
		
		if(registrationTask!=null) {
			registrationTask.cancel();
			registrationTask.unregister();
			registrationTask.destroy();
		}

		server.stop();
	}
}
