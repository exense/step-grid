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
package step.grid.client;

import step.grid.client.security.ClientSecurityConfiguration;
import step.grid.contextbuilder.ExecutionContextCacheConfiguration;

public class GridClientConfiguration {

	private long noMatchExistsTimeout = 10000;
	private long matchExistsTimeout = 60000;
	
	private int releaseSessionTimeout = 60000;
	private int reserveSessionTimeout = 10000;
	private int tokenExecutionInterruptionTimeout = 10000;

	private int maxConnectionRetries = 3;
	private long connectionRetryGracePeriod = 10_000;
	private int readTimeoutOffset = 3000;
	
	private boolean allowInvalidSslCertificates = false;

	private int maxStringLength = 50000000;

	private boolean useLocalAgentUrlIfAvailable = false;

	private ExecutionContextCacheConfiguration localTokenExecutionContextCacheConfiguration = new ExecutionContextCacheConfiguration();

	private ClientSecurityConfiguration security;

	public long getNoMatchExistsTimeout() {
		return noMatchExistsTimeout;
	}

	public void setNoMatchExistsTimeout(long noMatchExistsTimeout) {
		this.noMatchExistsTimeout = noMatchExistsTimeout;
	}

	public long getMatchExistsTimeout() {
		return matchExistsTimeout;
	}

	public void setMatchExistsTimeout(long matchExistsTimeout) {
		this.matchExistsTimeout = matchExistsTimeout;
	}

	public int getReleaseSessionTimeout() {
		return releaseSessionTimeout;
	}

	public void setReleaseSessionTimeout(int releaseSessionTimeout) {
		this.releaseSessionTimeout = releaseSessionTimeout;
	}

	public int getReserveSessionTimeout() {
		return reserveSessionTimeout;
	}

	public void setReserveSessionTimeout(int reserveSessionTimeout) {
		this.reserveSessionTimeout = reserveSessionTimeout;
	}

	public int getTokenExecutionInterruptionTimeout() {
		return tokenExecutionInterruptionTimeout;
	}

	public void setTokenExecutionInterruptionTimeout(int tokenExecutionInterruptionTimeout) {
		this.tokenExecutionInterruptionTimeout = tokenExecutionInterruptionTimeout;
	}

	public int getMaxConnectionRetries() {
		return maxConnectionRetries;
	}

	public void setMaxConnectionRetries(int maxConnectionRetries) {
		this.maxConnectionRetries = maxConnectionRetries;
	}

	public long getConnectionRetryGracePeriod() {
		return connectionRetryGracePeriod;
	}

	public void setConnectionRetryGracePeriod(long connectionRetryGracePeriod) {
		this.connectionRetryGracePeriod = connectionRetryGracePeriod;
	}

	public int getReadTimeoutOffset() {
		return readTimeoutOffset;
	}

	public void setReadTimeoutOffset(int readTimeoutOffset) {
		this.readTimeoutOffset = readTimeoutOffset;
	}

	public boolean isAllowInvalidSslCertificates() {
		return allowInvalidSslCertificates;
	}

	public void setAllowInvalidSslCertificates(boolean allowInvalidSslCertificates) {
		this.allowInvalidSslCertificates = allowInvalidSslCertificates;
	}

	public int getMaxStringLength() {
		return maxStringLength;
	}

	public void setMaxStringLength(int maxStringLength) {
		this.maxStringLength = maxStringLength;
	}

	public ExecutionContextCacheConfiguration getLocalTokenExecutionContextCacheConfiguration() {
		return localTokenExecutionContextCacheConfiguration;
	}

	public void setLocalTokenExecutionContextCacheConfiguration(ExecutionContextCacheConfiguration localTokenExecutionContextCacheConfiguration) {
		this.localTokenExecutionContextCacheConfiguration = localTokenExecutionContextCacheConfiguration;
	}

	public boolean isUseLocalAgentUrlIfAvailable() {
		return useLocalAgentUrlIfAvailable;
	}

	public void setUseLocalAgentUrlIfAvailable(boolean useLocalAgentUrlIfAvailable) {
		this.useLocalAgentUrlIfAvailable = useLocalAgentUrlIfAvailable;
	}

	public ClientSecurityConfiguration getSecurity() {
		return security;
	}

	public void setSecurity(ClientSecurityConfiguration security) {
		this.security = security;
	}
}
