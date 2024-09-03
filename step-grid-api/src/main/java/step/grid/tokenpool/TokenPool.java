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
package step.grid.tokenpool;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import step.grid.TokenWrapper;

public class TokenPool<P extends Identity, F extends Identity> implements Closeable {
	
	private final static Logger logger = LoggerFactory.getLogger(TokenPool.class);
	
	final AffinityEvaluator<P, F> affinityEval;
	
	final Map<String,Token<F>> tokens = new HashMap<>();
	
	final Map<String,Consumer<F>> returnTokenListeners = new ConcurrentHashMap<>();
	
	final List<WaitingPretender<P,F>> waitingPretenders = Collections.synchronizedList(new LinkedList<WaitingPretender<P,F>>());

	final List<RegistrationCallback<F>> tokenRegistrationCallbacks = new CopyOnWriteArrayList<>();
	
	long keepaliveTimeout;
	
	Timer keepaliveTimeoutCheckTimer;
	
	public TokenPool(AffinityEvaluator<P, F> affinityEval) {
		super();
		this.affinityEval = affinityEval;
		
		keepaliveTimeout = -1;
		
		keepaliveTimeoutCheckTimer = new Timer();
		keepaliveTimeoutCheckTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					keepaliveTimeoutCheck();
				} catch (Exception e) {
					logger.error("An error occurred while running timer.", e);
				}
			}
		}, 10000,10000);
	}

	public void addTokenRegistrationCallback(RegistrationCallback<F> callback) {
		tokenRegistrationCallbacks.add(callback);
	}

	public void removeTokenRegistrationCallback(RegistrationCallback<F> callback) {
		tokenRegistrationCallbacks.remove(callback);
	}
	
	public void setKeepaliveTimeout(long timeout) {
		keepaliveTimeout = timeout;
	}
	
	public F selectToken(P pretender, long timeout) throws TimeoutException, InterruptedException {
		return selectToken(pretender, timeout, timeout);
	}
	
	public F selectToken(P pretender, long matchExistsTimeout, long noMatchExistsTimeout) throws TimeoutException, InterruptedException {
		boolean poolContainsMatchingToken = false;
		
		synchronized (tokens) {
			MatchingResult matchingResult =  searchMatchesInTokenList(pretender);
			Token<F> bestMatch = matchingResult.bestAvailableMatch;
			if(matchingResult.bestAvailableMatch!=null) {
				if(logger.isDebugEnabled()) {
					logger.debug("Found token without queuing. Pretender=" + pretender.toString() + ". Token=" + bestMatch.toString());
				}
				bestMatch.available = false;
				return bestMatch.object;
			} else if(matchingResult.bestMatch!=null) {
				poolContainsMatchingToken = true;
			}
		}
		
		if(logger.isDebugEnabled()) {
			logger.debug("No free token found. Enqueuing... Pretender=" + pretender.toString());
		}
		WaitingPretender<P, F> waitingPretender = new WaitingPretender<P,F>(pretender);
		try {
			waitingPretenders.add(waitingPretender);
			
			synchronized (waitingPretender) {
				long waitTime = poolContainsMatchingToken?matchExistsTimeout:noMatchExistsTimeout;
				waitingPretender.wait(waitTime);	
			}

			if(waitingPretender.associatedToken!=null) {
				if(logger.isDebugEnabled()) {
					logger.debug("Found token after queuing. Pretender=" + pretender.toString() + ". Token=" + waitingPretender.associatedToken.toString());
				}
				return waitingPretender.associatedToken.object;
			} else {
				logger.warn("Timeout occurred while selecting token. Pretender=" + pretender.toString());
				throw new TimeoutException("Timeout occurred while selecting token.");
			}
		} finally {
			waitingPretenders.remove(waitingPretender);
		}
		
	}
	
	private class MatchingResult {
		
		Token<F> bestMatch;
		
		Token<F> bestAvailableMatch;

		public MatchingResult(Token<F> bestMatch, Token<F> bestAvailableMatch) {
			super();
			this.bestMatch = bestMatch;
			this.bestAvailableMatch = bestAvailableMatch;
		}
		
	}
	
	private MatchingResult searchMatchesInTokenList(P pretender) {
		Token<F> bestMatch = null;
		int bestScore = -1;
		
		Token<F> bestAvailableMatch = null;
		int bestAvailableScore = -1;
		
		for(Token<F> token:tokens.values()) {
			int score = affinityEval.getAffinityScore(pretender, token.object);
			if(score!=-1 && score > bestScore) {
				bestScore = score;
				bestMatch = token;
			}
			if(token.available) {
				if(score!=-1 && score > bestAvailableScore) {
					bestAvailableScore = score;
					bestAvailableMatch = token;
				}
			}
		}
		return new MatchingResult(bestMatch, bestAvailableMatch);
	}
	
	private void notifyWaitingPretendersWithoutMatchInTokenList() {
		synchronized (waitingPretenders) {
			for(WaitingPretender<P, F> waitingPretender:waitingPretenders) {
				if(!hasWaitingPretenderAMatchInTokenList(waitingPretender)) {
					synchronized (waitingPretender) {
						if(logger.isTraceEnabled()) {
							logger.trace("notifyWaitingPretendersWithoutMatchInTokenList, pretender: " + waitingPretender);
						}
						waitingPretender.notify();
					}
				}
			}
		}
	}
	
	private boolean hasWaitingPretenderAMatchInTokenList(WaitingPretender<P, F> waitingPretender) {
		MatchingResult matchingResult =  searchMatchesInTokenList(waitingPretender.pretender);
		return matchingResult.bestMatch!=null;
	}
	
	public void addReturnTokenListener(String tokenId, Consumer<F> consumer) {
		synchronized (tokens) {
			Token<F> token = tokens.get(tokenId);
			if(token.available) {
				callReturnTokenListener(token.getObject(), consumer);
			} else {
				returnTokenListeners.put(tokenId, consumer);
			}
		}
	}
	
	public void returnToken(F object) {
		synchronized (tokens) {
			if(logger.isDebugEnabled()) {
				logger.debug("Returning token. Token=" + object.toString());
			}
			Token<F> token = findToken(object);
			if(token.invalidated) {
				removeToken(token);
			} else {
				token.available = true;
				Consumer<F> listener = returnTokenListeners.remove(object.getID());
				if(listener!=null) {
					callReturnTokenListener(object, listener);
				}
				checkForMatchInPretenderWaitingQueue(token);
			}
		}
	}

	protected void callReturnTokenListener(F object, Consumer<F> listener) {
		try {
			listener.accept(object);
		} catch(Exception e) {
			logger.error("Error while calling listener for token " + object.getID(), e);
		}
	}

	private void removeToken(Token<F> token) {
		tokens.remove(token.getObject().getID());
		for (RegistrationCallback<F> callback: tokenRegistrationCallbacks) {
			try {
				callback.afterUnregistering(List.of(token.object));
			} catch (Exception e) {
				logger.error("Unexpected exception", e);
			}
		}
		notifyWaitingPretendersWithoutMatchInTokenList();
	}
	
	private Token<F> findToken(F object) {
		return tokens.get(object.getID());
	}
	
	
	public String offerToken(F object) {
		synchronized (tokens) {
			if(logger.isTraceEnabled()) {
				logger.trace("Offering token. Token=" + object.toString());
			}
			Token<F> token;
			Token<F> existingToken = findToken(object);
			if (existingToken != null) {
				token = existingToken;
			} else {
				token = new Token<>(object);
				token.available = true;
			}
			boolean allowed = tokenRegistrationCallbacks.stream().allMatch(cb -> cb.beforeRegistering(token.object));
			if (allowed) {
				if (existingToken == null) {
					// new token, put it in the map
					tokens.put(token.object.getID(), token);
					checkForMatchInPretenderWaitingQueue(token);
				}
				keepaliveToken(token);
				return token.getObject().getID();
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("one or more callbacks vetoed token registration, token is ignored and invalidated if present: {}", token.object);
				}
				invalidateToken(token);
				return null;
			}
		}
	}
	
	private void keepaliveTimeoutCheck() {
		if(keepaliveTimeout>0) {
			synchronized (tokens) {
				long now = System.currentTimeMillis();
				List<Token<F>> invalidTokens = new ArrayList<>();
				for(Token<F> token:tokens.values()) {
					if(token.lastTouch+keepaliveTimeout<now) {
						invalidTokens.add(token);
					}
				}
				for(Token<F> token:invalidTokens) {
					invalidateToken(token);					
				}
			}
		}
	}
	
	
	public void keepaliveToken(String id) {
		synchronized (tokens) {
			Token<F> token = tokens.get(id);
			keepaliveToken(token);
		}
	}
	
	private void keepaliveToken(Token<F> token) {
		token.lastTouch = System.currentTimeMillis();
	}
	
	public F getToken(String id) {
		synchronized (tokens) {
			Token<F> token = tokens.get(id);
			if(token != null) {
				return token.getObject();
			} else {
				return null;
			}
		}
	}
	
	public void invalidate(String id) {
		synchronized (tokens) {
			Token<F> token = tokens.get(id);
			invalidateToken(token);
		}
	}
	
	public void invalidateToken(F object) {
		synchronized (tokens) {
			Token<F> token = findToken(object);
			invalidateToken(token);
		}
	}
	
	private void invalidateToken(Token<F> token) {
		if(token!=null) {
			if(logger.isDebugEnabled()) {
				logger.debug("Invalidating token. Token=" + token.object);
			}
			token.invalidated = true;
			if(token.available) {
				removeToken(token);
			}
		}
	}
	
	
	private void checkForMatchInPretenderWaitingQueue(Token<F> token) {
		WaitingPretender<P, F> pretenderMatch = selectPretender(token);
		if(pretenderMatch!=null) {
			token.available = false;
			pretenderMatch.associatedToken = token;
			synchronized (pretenderMatch) {
				if(logger.isTraceEnabled()) {
					logger.trace("Pretender match found for token " + token.getObject().getID()
							+ " notifying pretender: " + pretenderMatch);
				}
				pretenderMatch.notify();
			}
		}
	}
	
	private WaitingPretender<P, F> selectPretender(Token<F> token) {
		synchronized (waitingPretenders) {
			for(WaitingPretender<P, F> pretender:waitingPretenders) {
				if(pretender.associatedToken==null && affinityEval.getAffinityScore(pretender.pretender, token.object)>=0) {
					return pretender;
				}
			}			
		}
		return null;
	}
	
	public int getSize() {
		return tokens.size();
	}
	
	public List<F> getTokens() {
		synchronized (tokens) {
			return tokens.values().stream().map(t->t.getObject()).collect(Collectors.toList());
		}
	}
	
	public List<P> getWaitingPretenders() {
		synchronized (waitingPretenders) {
			List<P> result = new ArrayList<>(waitingPretenders.size());
			for(WaitingPretender<P, F> waitingPretender:waitingPretenders) {
				result.add(waitingPretender.pretender);
			}
			return result;
		}
	}

	@Override
	public void close() throws IOException {
		keepaliveTimeoutCheckTimer.cancel();
	}
}
