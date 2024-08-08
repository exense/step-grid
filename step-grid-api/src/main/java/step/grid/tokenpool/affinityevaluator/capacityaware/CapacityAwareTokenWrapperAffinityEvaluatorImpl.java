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
package step.grid.tokenpool.affinityevaluator.capacityaware;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ch.exense.commons.io.FileWatchService;
import step.grid.TokenWrapper;
import step.grid.TokenWrapperState;
import step.grid.tokenpool.AffinityEvaluator;
import step.grid.tokenpool.Identity;
import step.grid.tokenpool.TokenPool;
import step.grid.tokenpool.affinityevaluator.TokenPoolAware;
import step.grid.tokenpool.affinityevaluator.TokenWrapperAffinityEvaluatorImpl;
import step.grid.tokenpool.affinityevaluator.capacityaware.CapacityAwareTokenWrapperAffinityEvaluatorConf.Agent;

/**
 * An {@link AffinityEvaluator} that takes the token usage per <b>agent host</b> into account and thus 
 * gives priority to tokens located on agent hosts with the lowest token usage. <br>
 * 
 * This {@link AffinityEvaluator} makes it also possible to define a maximal capacity per <b>agent host</b>.
 *
 */
public class CapacityAwareTokenWrapperAffinityEvaluatorImpl extends TokenWrapperAffinityEvaluatorImpl implements TokenPoolAware, Closeable {

	private TokenPool<Identity, TokenWrapper> tokenPool;
	private CapacityAwareTokenWrapperAffinityEvaluatorConf conf;
	private FileWatchService fileWatchService;
	
	private static final int MAX_SCORE = 1000;

	public CapacityAwareTokenWrapperAffinityEvaluatorImpl() {
		super();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void setTokenPool(TokenPool<?, ?> tokenPool) {
		this.tokenPool = (TokenPool<Identity, TokenWrapper>) tokenPool;
	}
	
	@Override
	public void setProperties(Map<String, String> properties) {
		super.setProperties(properties);
		
		if(properties!=null) {
			String configuration = properties.get("configuration");
			if(configuration != null) {
				File configurationFile = new File(configuration);
				init(configurationFile);
			} else {
				throw new RuntimeException("Error while initializing "+CapacityAwareTokenWrapperAffinityEvaluatorImpl.class.getSimpleName()+": Missing property 'configuration'");
			}
		} else {
			throw new RuntimeException("Error while initializing "+CapacityAwareTokenWrapperAffinityEvaluatorImpl.class.getSimpleName()+": Missing properties");
		}
	}
	
	protected void init(File configurationFile) {
		parseConf(configurationFile);
		
		fileWatchService = new FileWatchService();
		fileWatchService.register(configurationFile, () -> {
			parseConf(configurationFile);
		});
	}
	
	protected void parseConf(File configurationFile) {
		CapacityAwareTokenWrapperAffinityEvaluatorConfParser confParser = new CapacityAwareTokenWrapperAffinityEvaluatorConfParser();
		try {
			conf = confParser.parse(configurationFile);
		} catch (Exception e) {
			throw new RuntimeException("Error while parsing configuration file "+configurationFile.getAbsolutePath(), e);
		}
	}

	public int getAffinityScore(Identity i1, TokenWrapper i2) {
		final String agentUrl = i2.getAgent().getAgentUrl();
	
		// parse the agentUrl to get the agent host on which the agent host usage calculation is based
		String agentHost = getHost(agentUrl);

		/// the token usage per agent host
		AtomicInteger agentHostUsage = new AtomicInteger(0);
		
		// calculate the token usage per agent host
		tokenPool.getTokens().forEach(tokenWrapper->{
			if(tokenWrapper.getState() == TokenWrapperState.IN_USE && getHost(tokenWrapper.getAgent().getAgentUrl()).equals(agentHost)) {
				agentHostUsage.incrementAndGet();
			}
		});

		// get the configuration for this agent host
		Agent agentHostConf = conf.getAgents().stream().filter(a->a.getHostname().equals(agentHost)).findFirst().orElse(null);
		
		int currentAgentUsage = agentHostUsage.get();
		if(agentHostConf != null && agentHostConf.getCapacity() != -1 && currentAgentUsage>=agentHostConf.getCapacity()) {
			// the token usage of this agent host reached the maximal defined capacity => thus return -1
			return -1;
		} else {
			int defaultAffinityScore = super.getAffinityScore(i1, i2);
			
			// only the sign of the default affinity evaluator is considered here i.e. if it is matching or not.
			int signumOfDefaultAffinityScore = Integer.signum(defaultAffinityScore);
			
			// returning a score which is inversely proportional to the token usage or -1 if the default affinity score is equal to -1
			return Math.max(-1, Math.max(1, (MAX_SCORE-currentAgentUsage))*signumOfDefaultAffinityScore);
		}
	}

	protected String getHost(final String agentUrl) {
		URL url;
		try {
			url = new URL(agentUrl);
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error while parsing url "+agentUrl, e);
		}
		return url.getHost();
	}

	@Override
	public void close() throws IOException {
		fileWatchService.close();
	}
}
