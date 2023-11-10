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
package step.grid.agent.conf;

import step.grid.filemanager.FileManagerConfiguration;

import java.util.List;
import java.util.Map;

public class AgentConf {
	
	String gridHost;

	String fileServerHost;
	
	Integer agentPort;
	
	String agentHost;
	
	String agentUrl;
	
	boolean ssl = false;
	String keyStorePath;
	String keyStorePassword;
	String keyManagerPassword;


	boolean exposeMetrics = false;
	
	String workingDir;
	
	Integer registrationPeriod = 10000;

	Integer registrationOffset = 0;
	
	Integer gridReadTimeout = 3000;
	
	Integer gridConnectTimeout = 3000;

	List<TokenGroupConf> tokenGroups;
	
	Map<String, String> properties;
	
	Long gracefulShutdownTimeout;

	FileManagerConfiguration fileManagerConfiguration = new FileManagerConfiguration();

	public AgentConf() {
		super();
	}

	public AgentConf(String gridHost, Integer agentPort, String agentUrl) {
		super();
		this.agentPort = agentPort;
		this.agentUrl = agentUrl;
		setGridHost(gridHost);
	}
	
	public AgentConf(String gridHost, String agentHost) {
		super();
		this.agentHost = agentHost;
		setGridHost(gridHost);
	}

	public AgentConf(String gridHost, Integer agentPort, String agentUrl, Integer registrationPeriod) {
		super();
		this.agentPort = agentPort;
		this.agentUrl = agentUrl;
		this.registrationPeriod = registrationPeriod;
		setGridHost(gridHost);
	}

	public String getGridHost() {
		return gridHost;
	}

	public void setGridHost(String gridHost) {
		this.gridHost = gridHost.endsWith("/") ?
				gridHost.substring(0,gridHost.length()-1) :
				gridHost;
	}

	public String getFileServerHost() {
		return fileServerHost;
	}

	public void setFileServerHost(String fileServerHost) {
		this.fileServerHost = fileServerHost;
	}

	public Integer getAgentPort() {
		return agentPort;
	}

	public void setAgentPort(Integer agentPort) {
		this.agentPort = agentPort;
	}
	
	public String getAgentHost() {
		return agentHost;
	}
	
	public void setAgentHost(String agentHost) {
		this.agentHost = agentHost;
	}

	public String getAgentUrl() {
		return agentUrl;
	}

	public void setAgentUrl(String agentUrl) {
		this.agentUrl = agentUrl;
	}

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public String getKeyStorePath() {
		return keyStorePath;
	}

	public void setKeyStorePath(String keyStorePath) {
		this.keyStorePath = keyStorePath;
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	public void setKeyStorePassword(String keyStorePassword) {
		this.keyStorePassword = keyStorePassword;
	}

	public String getKeyManagerPassword() {
		return keyManagerPassword;
	}

	public void setKeyManagerPassword(String keyManagerPassword) {
		this.keyManagerPassword = keyManagerPassword;
	}

	public boolean isExposeMetrics() { return exposeMetrics; }

	public void setExposeMetrics(boolean exposeMetrics) { this.exposeMetrics = exposeMetrics; }

	public List<TokenGroupConf> getTokenGroups() {
		return tokenGroups;
	}

	public void setTokenGroups(List<TokenGroupConf> tokenGroups) {
		this.tokenGroups = tokenGroups;
	}

	public Integer getRegistrationPeriod() {
		return registrationPeriod;
	}

	public void setRegistrationPeriod(Integer registrationPeriod) {
		this.registrationPeriod = registrationPeriod;
	}

	public Integer getRegistrationOffset() {
		return registrationOffset;
	}

	public void setRegistrationOffset(Integer registrationOffset) {
		this.registrationOffset = registrationOffset;
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}

	public Integer getGridReadTimeout() {
		return gridReadTimeout;
	}

	public void setGridReadTimeout(Integer gridReadTimeout) {
		this.gridReadTimeout = gridReadTimeout;
	}

	public Integer getGridConnectTimeout() {
		return gridConnectTimeout;
	}

	public void setGridConnectTimeout(Integer gridConnectTimeout) {
		this.gridConnectTimeout = gridConnectTimeout;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}

	public Long getGracefulShutdownTimeout() {
		return gracefulShutdownTimeout;
	}

	public void setGracefulShutdownTimeout(Long gracefulShutdownTimeout) {
		this.gracefulShutdownTimeout = gracefulShutdownTimeout;
	}

	public FileManagerConfiguration getFileManagerConfiguration() {
		return fileManagerConfiguration;
	}

	public void setFileManagerConfiguration(FileManagerConfiguration fileManagerConfiguration) {
		this.fileManagerConfiguration = fileManagerConfiguration;
	}
}
