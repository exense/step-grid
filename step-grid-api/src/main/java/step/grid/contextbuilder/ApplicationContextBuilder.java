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
package step.grid.contextbuilder;

import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.grid.filemanager.FileManagerException;

/**
 * This class provides an API for the creation of hierarchical classloaders.
 * The creation of a classloader tree and the execution of a {@link Callable}
 * in the created context using this class works basically as follow:
 * 
 * <pre>
 *  ApplicationContextBuilder builder = new ApplicationContextBuilder()
 *  builder.pushContext(...)
 *  builder.pushContext(...)
 *  builder.runInContext(...)
 * </pre>
 * 
 * It supports the creation of {@link ClassLoader} trees structures using a fork 
 * mechanism. For instance:
 * 
 * <pre>
 *  ApplicationContextBuilder builder = new ApplicationContextBuilder()
 *  builder.pushContext(...)
 *  builder.fork("newBranchName")
 *  builder.pushContext("newBranchName", ...)
 *  builder.runInContext("newBranchName", ...)
 * </pre> 
 * 
 * This code will result in a tree of {@link ClassLoader} having to branches:
 * the master branch (the default one) and the branch called "newBranchName"
 * This branches can be selected for execution using builder.runInContext("branchName",...)
 * 
 * @author Jerome Comte
 *
 */
public class ApplicationContextBuilder implements AutoCloseable {
	
	public static final String MASTER = "master";

	private static final Logger logger = LoggerFactory.getLogger(ApplicationContextBuilder.class);
		
	private ConcurrentHashMap<String, Branch> branches = new ConcurrentHashMap<>();
	private ScheduledExecutorService scheduledPool;
	private ScheduledFuture<?> future;

	protected class Branch implements AutoCloseable{
		
		private ApplicationContext rootContext;
		private final String branchName;
		
		private final ThreadLocal<ApplicationContext> currentContexts = new ThreadLocal<>();

		protected Branch(ApplicationContext rootContext, String branchName) {
			super();
			this.rootContext = rootContext;
			this.branchName = branchName;
			reset();
		}
		
		protected void reset() {
			currentContexts.set(rootContext);
		}
		
		public Branch fork(String newBranchName) {
			ApplicationContext currentContext = currentContexts.get();
			Branch newBranch = new Branch(currentContext, newBranchName);
			ApplicationContextBuilder.this.branches.put(newBranchName, newBranch);
			return newBranch;
		}

		protected ThreadLocal<ApplicationContext> getCurrentContexts() {
			return currentContexts;
		}

		public void cleanup() {
			if (logger.isDebugEnabled()) {
				logger.debug("Cleaning up branch {}", branchName);
			}
			//Root context class loader itself should not be cleaned-up, only its children
			if (rootContext != null) {
				rootContext.cleanup();
			}
		}

		@Override
		public void close() throws Exception {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing branch {}", branchName);
			}
			cleanup();
		}
	}

	/**
	 * Creates a new instance of {@link ApplicationContextBuilder} using the {@link ClassLoader} 
	 * of this class as root {@link ClassLoader}
	 */
	public ApplicationContextBuilder() {
		this(ApplicationContextBuilder.class.getClassLoader());
	}
	
	/**
	 * Creates a new instance of {@link ApplicationContextBuilder} using the specified {@link ClassLoader} 
	 * 
	 * @param rootClassLoader the {@link ClassLoader} to be used as root of this context
	 */
	public ApplicationContextBuilder(ClassLoader rootClassLoader) {
		ApplicationContext rootContext = new ApplicationContext(rootClassLoader, "rootContext");
		Branch branch = new Branch(rootContext, MASTER);
		branches.put(MASTER, branch);
		scheduleCleanupJob();
	}
	
	/**
	 * Reset the context of all branches for this thread.
	 * After calling this method the current context will point to
	 * the root context of each branches.
	 */
	public void resetContext() {
		branches.values().forEach(branch->branch.reset());
	}

	/**
	 * Reset the context of the specified branch for this thread.
	 * 
	 * After calling this method the current context of this thread will point to
	 * the root context of the specified branch
	 * 
	 * @param branchName the name of the branch for each the context should be reseted
	 */
	public void resetContext(String branchName) {
		getBranch(branchName).reset();
	}
	
	private Branch getBranch(String branchName) {
		Branch branch = branches.get(branchName);
		if(branch == null) {
			throw new RuntimeException("No branch found with name "+branchName);
		}
		return branch;
	}
	
	/**
	 * Create a new branch based on the current context of the default branch
	 * 
	 * @param newBranchName the name of the new branch to be created
	 */
	public void forkCurrentContext(String newBranchName) {
		getBranch(MASTER).fork(newBranchName);
	}
	
	/**
	 * Create a new branch based on the current context of the branch specified as input
	 * 
	 * @param originBranchName the branch to be forked
	 * @param newBranchName the name of the new branch to be created
	 */
	public void forkCurrentContext(String originBranchName, String newBranchName) {
		getBranch(originBranchName).fork(newBranchName);
	}
	
	/**
	 * Push a new context (resulting in a {@link ClassLoader}) to the stack for the current thread
	 * 
	 * @param descriptor the descriptor of the context to be pushed 
	 * @throws ApplicationContextBuilderException
	 */
	public ApplicationContextControl pushContext(ApplicationContextFactory descriptor) throws ApplicationContextBuilderException {
		return pushContext(MASTER, descriptor);
	}
	
	/**
	 * Push a new context (resulting in a {@link ClassLoader}) to the stack of the branch specified as input for the current thread
	 * 
	 * @param branchName the name of the branch to push the new context onto
	 * @param descriptor the descriptor of the context to be pushed 
	 * @throws ApplicationContextBuilderException
	 */
	public ApplicationContextControl pushContext(String branchName, ApplicationContextFactory descriptor) throws ApplicationContextBuilderException {
		synchronized(this) {
			String contextKey = descriptor.getId();
			if(logger.isDebugEnabled()) {
				logger.debug("Pushing context "+contextKey+" to branch "+branchName);
			}
 			ThreadLocal<ApplicationContext> branchCurrentContext = getBranch(branchName).getCurrentContexts();
			ApplicationContext parentContext = branchCurrentContext.get();
			if(parentContext==null) {
				throw new RuntimeException("The current context is null. This should never occur");
			}
			
			ApplicationContext context;
			if(!parentContext.containsChildContextKey(contextKey)) {
				if(logger.isDebugEnabled()) {
					logger.debug("Context "+contextKey+" doesn't exist on branch "+branchName+". Creating new context");
				}

				try {
					context = new ApplicationContext(descriptor, parentContext, contextKey);
				} catch (FileManagerException e) {
					throw new ApplicationContextBuilderException(e);
				}
				parentContext.putChildContext(contextKey, context);
			} else {
				if(logger.isDebugEnabled()) {
					logger.debug("Context "+contextKey+" existing on branch. Reusing it.");
				}
				context = parentContext.getChildContext(contextKey);
				try {
					if(descriptor.requiresReload()) {
						if(logger.isDebugEnabled()) {
							logger.debug("Context "+contextKey+" requires reload. Reloading...");
						}
						context.reloadContext(descriptor, context);
					}
				} catch (FileManagerException e) {
					throw new ApplicationContextBuilderException(e);
				}
			}
			context.registerUsage();
			branchCurrentContext.set(context);
			return new ApplicationContextControl(context);
		}
	}
	
	public ApplicationContext getCurrentContext() {
		return getCurrentContext(MASTER);
	}
	
	public ApplicationContext getCurrentContext(String branch) {
		return getBranch(branch).getCurrentContexts().get();
	}
	
	/**
	 * Execute the callable passed as argument in the current context of this thread
	 * 
	 * @param callable the callable to be executed
	 * @return the result of the callable
	 * @throws Exception
	 */
	public <T> T runInContext(Callable<T> callable) throws Exception {
		return runInContext(MASTER, callable);
	}
	
	/**
	 * Execute the callable passed as argument in the current context of this thread on the branch specified as input
	 * 
	 * @param branchName the name of the branch to be used for execution
	 * @param runnable the callable to be executed
	 * @return the result of the callable
	 * @throws Exception
	 */
	public <T> T runInContext(String branchName, Callable<T> runnable) throws Exception {
		ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(getCurrentContext(branchName).getClassLoader());
		try {
			return runnable.call();
		} finally {
			Thread.currentThread().setContextClassLoader(previousCl);
		}
	}

	private void scheduleCleanupJob() {
		scheduledPool = Executors.newScheduledThreadPool(1);
		future = scheduledPool.scheduleAtFixedRate(() -> {
			//We may have multiple instance of application context builder
			// Thread.currentThread().setName("ApplicationContextCleanupThread");
			try {
				this.cleanupCache();
			} catch (Throwable e) {
				logger.error("Unhandled error while running the application context builder clean up task.", e);
			}
		}, 1, 1, TimeUnit.MINUTES);
	}

	private void cleanupCache() {
		synchronized(this) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cleanup all application contexts");
			}
			branches.forEach((k, b) -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Cleanup application contexts for branch {}", k);
				}
				b.cleanup();
			});
		}
	}

	@Override
	public void close() {
		if(logger.isDebugEnabled()) {
			logger.debug("Closing application context builder");
		}
		if (future != null) {
			future.cancel(false);
		}
		if (scheduledPool != null) {
			scheduledPool.shutdown();
			try {
				scheduledPool.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				logger.error("Timeout occurred while stopping the file manager clean up task.");
			}
		}
		if(logger.isDebugEnabled()) {
			logger.debug("Closing all application contexts");
		}
		branches.forEach((k,b) -> {
			if(logger.isDebugEnabled()) {
				logger.debug("Closing application contexts for branch {}", k);
			}
			try {
				b.close();
			} catch (Exception e) {
				logger.error("Unable to close the branch {}", k, e);
			}
		});
	}
}
