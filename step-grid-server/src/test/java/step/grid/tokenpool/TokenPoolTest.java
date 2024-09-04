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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import junit.framework.Assert;


@SuppressWarnings("resource")
public class TokenPoolTest {
	
	private static final Logger logger = LoggerFactory.getLogger(TokenPoolTest.class);
	
	@Test
	public void test_Match_Positive_1() throws Exception {
		TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");		
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest(Pattern.compile("red"), true));
		
		IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 10);
		Assert.assertNotNull(selectedIdentityImpl);
		
	}
	
	@Test
	public void test_Match_Positive_2() throws Exception {
		TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		//token.addInterest(new Se)
		
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addAttribute("color", "green");
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		
		IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 10);
		Assert.assertNotNull(selectedIdentityImpl);
	}
	
	@Test
	public void test_Match_Negative_2() {
		TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		//token.addInterest(new Se)
		
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addAttribute("color", "yellow");
		pretender.addInterest("color",new Interest( Pattern.compile("green"), true));
		
		Exception e1 = null;
		try {
			pool.selectToken(pretender, 10);
		} catch (Exception e) {
			e1 = e;
		}
		Assert.assertNotNull(e1);
		
	}
	
	@Test
	public void test_Match_Preference_1() throws Exception {
		TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		pool.offerToken(token);		
		
		token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "triangle");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addAttribute("color", "green");
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		pretender.addInterest("shape",new Interest( Pattern.compile("circle"), false));
		
		IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 100);
		Assert.assertNotNull(selectedIdentityImpl);
		Assert.assertEquals("red", selectedIdentityImpl.getAttributes().get("color"));
		Assert.assertEquals("circle", selectedIdentityImpl.getAttributes().get("shape"));
		
	}
	
	@Test
	public void test_Match_Preference_2() throws Exception {
		TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		token.addAttribute("id", "1");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		pool.offerToken(token);		
		
		token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		token.addAttribute("id", "2");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		token.addInterest("shape",new Interest( Pattern.compile("line"), false));
		pool.offerToken(token);		
		
		token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "triangle");
		token.addAttribute("id", "3");
		token.addInterest("color",new Interest( Pattern.compile("green"), true));
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addAttribute("color", "green");
		pretender.addAttribute("shape", "line");
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		pretender.addInterest("shape",new Interest( Pattern.compile("circle"), false));
		
		IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 100);
		Assert.assertNotNull(selectedIdentityImpl);
		Assert.assertEquals("red", selectedIdentityImpl.getAttributes().get("color"));
		Assert.assertEquals("circle", selectedIdentityImpl.getAttributes().get("shape"));
		Assert.assertEquals("2", selectedIdentityImpl.getAttributes().get("id"));
		
	}
	
	@Test
	public void test_Pool_Select() {
		TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		//token.addInterest(new Se)
		
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest( Pattern.compile("green"), true));
		
		Exception e1 = null;
		try {
			pool.selectToken(pretender, 10);
		} catch (Exception e) {
			e1 = e;
		}
		Assert.assertNotNull(e1);
		
	}
	
	@Test
	public void test_Pool_Invalidate_1() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("id", "1");
		
		pool.offerToken(token);
		
		pool.invalidateToken(token);
		
		Assert.assertEquals(0, pool.getSize());
	}
	
	@Test
	public void test_Pool_Invalidate_2() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("id", "1");
		
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("id",new Interest(Pattern.compile("1"), true));
		
		IdentityImpl selectedToken = pool.selectToken(pretender, 10);
		pool.invalidateToken(selectedToken);
		
		pool.returnToken(token);
		
		Assert.assertEquals(0, pool.getSize());
	}
	
	@Test
	public void test_Pool_Invalidate_3() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("id", "1");
		
		pool.offerToken(token);
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("id",new Interest(Pattern.compile("1"), true));
		
		IdentityImpl selectedToken = pool.selectToken(pretender, 10);
		pool.invalidate(selectedToken.getID());
		
		pool.returnToken(token);
		
		Assert.assertEquals(0, pool.getSize());
	}
	
	@Test
	public void test_Pool_NotifyAfterTokenRemove() throws InterruptedException {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		//token.addInterest(new Se)
		
		// Offer one token to the pool
		pool.offerToken(token);
		
		final IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		
		IdentityImpl selectedIdentityImpl = null;
		Exception e1 = null;

		// Select the unique token of the pool and keep it reserved
		try {
			selectedIdentityImpl = pool.selectToken(pretender, 10);
		} catch (Exception e) {
			e1 = e;
		}
		
		// Asserts that the token could be selected
		Assert.assertNotNull(selectedIdentityImpl);
		Assert.assertNull(e1);
		
		final List<Exception> l = new ArrayList<>();
		
		final AtomicBoolean tokenInvalidated = new AtomicBoolean(false);
		
		Semaphore s = new Semaphore(0);
		Thread t = new Thread() {

			@Override
			public void run() {
				// notify the thread start
				s.release();
				try {
					// Select a token and wait indefinitely (matchExsistsTimeout=0)
					pool.selectToken(pretender, 0, 1);
				} catch (TimeoutException e) {
					if(tokenInvalidated.get()) {
						// This is the expected path
						l.add(e);
					} else {
						l.add(new Exception("Timeout occurred before token invalidation"));
					}
				} catch (Exception e) {
					l.add(e);
				}
			}
			
		};
		
		t.start();
		
		// wait for the thread to start
		s.acquire();
		
		// Waiting here to reduce probability of SED-207 "No timeout in "selectToken" when a token is invalidated" to occur
		Thread.sleep(1000);
		tokenInvalidated.set(true);
		// Invalidate the token => this should lead to a timeout in the token selection as no match exists anymore after 
		// token invalidation
		pool.invalidateToken(token);
		pool.returnToken(token);
		
		// wait max 1 hour. 
		// Avoid a freeze of the build if no timeout occurs
		t.join(10000);
		
		// Asserts that the TimeoutException has been thrown
		Assert.assertEquals(1, l.size());
		Assert.assertTrue(l.get(0) instanceof TimeoutException);
	}

	/**
	 * This test covers the case where a token is invalidated while another thread is waiting for a token that doesn't exist in the grid at selection time.
	 * In this case the token invalidation should not interrupt the selection.
	 * @throws Exception
	 */
	@Test
	public void test_Pool_NotifyAfterTokenRemove_NoMatchAtSelection() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<>());

		final IdentityImpl tokenBlue = new IdentityImpl();
		tokenBlue.addAttribute("color", "blue");

		final IdentityImpl tokenRed = new IdentityImpl();
		tokenRed.addAttribute("color", "red");

		pool.offerToken(tokenBlue);

		// Invalidate the blue token after 100ms
		(new Thread(() -> {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
			pool.invalidateToken(tokenBlue);
		})).start();

		// Offer the red token after 500ms
		(new Thread(() -> {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {}
			pool.offerToken(tokenRed);
		})).start();

		// Select the red token
		IdentityImpl tokenRedPretender = new IdentityImpl();
		tokenRedPretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		IdentityImpl token = pool.selectToken(tokenRedPretender, 5000, 5000);

		// Ensure the red token could be selected
		Assert.assertNotNull(token);
	}
	
	@Test
	public void test_Pool_WaitingQueue() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		//token.addInterest(new Se)
		
		(new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
				pool.offerToken(token);
			}
		}).start();
		
		IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));

		IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 1000);
		Assert.assertNotNull(selectedIdentityImpl);
		
	}
	
	/**
	 * Test the {@link TokenPool} in parallel without contention i.e. the same number of tokens as the number of threads
	 */
	@Test
	public void test_Pool_Parallel_1TokenPerThread() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		final IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		
		final int testDurationMs = 1000;
		final int nThreads = 10;
		final int sleepTimeNs = 1000;
		
		ExecutorService e = Executors.newFixedThreadPool(nThreads+1);
		
		long t1 = System.currentTimeMillis();
		List<Future<Boolean>> futures = new ArrayList<>();
		for(int i=0;i<nThreads;i++) {
			final IdentityImpl token = new IdentityImpl();
			token.addAttribute("color", "red");
			token.addAttribute("shape", "circle");
			pool.offerToken(token);
			
			
			Future<Boolean> f = e.submit(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					while(System.currentTimeMillis()-t1<testDurationMs) {
						IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 100);
						try {
							Assert.assertNotNull(selectedIdentityImpl);
							Assert.assertFalse(selectedIdentityImpl.used);
							selectedIdentityImpl.used = true;
							
							Thread.sleep(0, sleepTimeNs);
						} finally {
							selectedIdentityImpl.used = false;
							pool.returnToken(selectedIdentityImpl);
						}
					}
					return true;
				}
			});
			futures.add(f);
		}
		
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "blue");
		token.addAttribute("shape", "circle");
		
		Future<Boolean> offeringThread = e.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				while(System.currentTimeMillis()-t1<testDurationMs) {
					pool.offerToken(token);
					Thread.sleep(0, sleepTimeNs);
				}
				return true;
			}
		});
		
		for(Future<Boolean> f:futures) {
			f.get();			
		}
		
		offeringThread.get();
		
		e.shutdown();
		
		Assert.assertEquals(11, pool.getSize());
	}

	
	
	/**
	 * Test the {@link TokenPool} with more threads than tokens
	 * 
	 */
	@Test
	public void test_Pool_Parallel_with_contention() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		pool.offerToken(token);
		
		int otherTokenCount = 4;
		
		for(int i=0;i<otherTokenCount;i++) {
			IdentityImpl otherToken = new IdentityImpl();
			otherToken.addAttribute("color", "red");
			otherToken.addAttribute("shape", "circle");
			pool.offerToken(otherToken);
		}
		//token.addInterest(new Se)
		
		final IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		
		final int testDurationMs = 1000;
		final int nThreads = 10;
		final int sleepTimeNs = 1000;
		
		ExecutorService e = Executors.newFixedThreadPool(nThreads+1);
		
		long t1 = System.currentTimeMillis();
		List<Future<Boolean>> futures = new ArrayList<>();
		for(int i=0;i<nThreads;i++) {
			Future<Boolean> f = e.submit(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					while(System.currentTimeMillis()-t1<testDurationMs) {
						IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, testDurationMs);
						try {
							Assert.assertNotNull(selectedIdentityImpl);
							Assert.assertFalse(selectedIdentityImpl.used);
							selectedIdentityImpl.used = true;
							
							Thread.sleep(0, sleepTimeNs);
						} finally {
							selectedIdentityImpl.used = false;
							pool.returnToken(selectedIdentityImpl);
						}
					}
					return true;
				}
			});
			futures.add(f);
		}
		
		Future<Boolean> offeringThread = e.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				while(System.currentTimeMillis()-t1<testDurationMs) {
					pool.offerToken(token);
					Thread.sleep(0, sleepTimeNs);					
				}
				return true;
			}
		});
		
		// The test shouldn't last longer than the specified duration
		// + a few seconds for eventual process pauses (Garbage collections, etc)
		for(Future<Boolean> f:futures) {
			f.get((int)(testDurationMs+2), TimeUnit.SECONDS);			
		}
		
		offeringThread.get();
		
		e.shutdown();
		
		Assert.assertEquals(1+otherTokenCount, pool.getSize());	
	}
	
	public void test_Pool_Perf_Poolsize() throws Exception {
		final TokenPool<IdentityImpl, IdentityImpl> pool = new TokenPool<>(new SimpleAffinityEvaluator<IdentityImpl, IdentityImpl>());
		
		final IdentityImpl pretender = new IdentityImpl();
		pretender.addInterest("color",new Interest( Pattern.compile("red"), true));
		
		final int nIterations = 1000;
		final int poolSize = 10000; 
		
		for(int i=0;i<poolSize;i++) {
			final IdentityImpl token = new IdentityImpl();
			token.addAttribute("color", UUID.randomUUID().toString());
			token.addAttribute("shape", "circle");
			pool.offerToken(token);
		}
		
		final IdentityImpl token = new IdentityImpl();
		token.addAttribute("color", "red");
		token.addAttribute("shape", "circle");
		pool.offerToken(token);
		
		long t1 = System.currentTimeMillis();
		
		for(int i=0;i<nIterations;i++) {
			IdentityImpl selectedIdentityImpl = pool.selectToken(pretender, 1000);
			try {
				Assert.assertNotNull(selectedIdentityImpl);
				Assert.assertFalse(selectedIdentityImpl.used);
				selectedIdentityImpl.used = true;
			} catch (Throwable e) {
				logger.error("Unexpected error", e);
			} finally {
				selectedIdentityImpl.used = false;
				pool.returnToken(selectedIdentityImpl);
			}
		}
		
		long duration = (System.currentTimeMillis()-t1);
		double durationPerSelection = duration / (1.0*nIterations);
		System.out.println("Duration: " + duration);
		System.out.println("Duration per iteration [ms]: " + durationPerSelection);

		//Assert.assertTrue(duration<testDurationMs + 100);
	}
}
