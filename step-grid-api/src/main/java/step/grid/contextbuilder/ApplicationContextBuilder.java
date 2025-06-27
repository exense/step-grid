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

import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import step.grid.filemanager.FileManagerException;
import step.grid.threads.NamedThreadFactory;

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

	private final long cleanupTTLMilliseconds;
	private ScheduledExecutorService scheduledPool;
	private ScheduledFuture<?> future;
	private final ExecutionContextCacheConfiguration executionContextCacheConfiguration;

	protected class Branch {
		
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

		public void close() {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing branch {}", branchName);
			}
			if (rootContext != null) {
				rootContext.close();
			}
		}

		public void cleanup(long cleanupTime) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cleaning up branch {}", branchName);
			}
			if (rootContext != null) {
				rootContext.cleanup(cleanupTime);
			}
		}
	}

	public class ApplicationContext {

		private final AtomicInteger usage = new AtomicInteger(0);

		private volatile long lastUsage = System.currentTimeMillis();

		private final String applicationContextId;

		private final ApplicationContext parentContext;

		private ClassLoader classLoader;

		private ApplicationContextFactory descriptor;

		private Map<String, ApplicationContext> childContexts = new ConcurrentHashMap<>();

		private ConcurrentHashMap<String, ContextObjectWrapper> contextObjects = new ConcurrentHashMap<>();
		protected final boolean cleanable;

		protected ApplicationContext(ApplicationContextFactory descriptor, ApplicationContext parentContext, String applicationContextId, boolean cleanable) throws FileManagerException {
			super();
			this.descriptor = descriptor;
			this.applicationContextId = applicationContextId;
			this.parentContext = parentContext;
			this. cleanable = cleanable;
			buildClassLoader(parentContext);
		}

		protected ApplicationContext(ClassLoader classLoader, String applicationContextId) {
			super();
			this.classLoader = classLoader;
			this.applicationContextId = applicationContextId;
			this.descriptor = null;
			this.parentContext = null;
			this.cleanable = false;
		}

		public Object get(Object key) {
			ContextObjectWrapper contextObjectWrapper = contextObjects.get(key);
			if(contextObjectWrapper != null) {
				return contextObjectWrapper.object;
			} else {
				return null;
			}
		}

		public Object computeIfAbsent(String key, Function<? super String, Object> mappingFunction) {
			return contextObjects.computeIfAbsent(key, k -> new ContextObjectWrapper(mappingFunction.apply(k), false)).object;
		}

		public Object computeObjectToBeClosedWithContextIfAbsent(String key, Function<? super String, AutoCloseable> mappingFunction) {
			return contextObjects.computeIfAbsent(key, k -> new ContextObjectWrapper(mappingFunction.apply(k), true)).object;
		}

		public Object put(String key, Object value) {
			return contextObjects.put(key, new ContextObjectWrapper(value, false)).object;
		}

		public Object putObjectToBeClosedWithContext(String key, AutoCloseable value) {
			return contextObjects.put(key, new ContextObjectWrapper(value, true)).object;
		}

		public ClassLoader getClassLoader() {
			return classLoader;
		}

		public void registerUsage() {
			usage.incrementAndGet();
			lastUsage = System.currentTimeMillis();
		}

		public void releaseUsage() {
			synchronized (ApplicationContextBuilder.this) {
				int currentUsage = usage.decrementAndGet();
				lastUsage = System.currentTimeMillis();
				if (logger.isTraceEnabled()) {
					logger.trace("Release usage of application context {}. new usage {}", applicationContextId, currentUsage);
				}
				if (currentUsage == 0 && cleanable && cleanupTTLMilliseconds == 0) {
					closeAndCleanupFromParent();
				}
			}
		}

		private void closeAndCleanupFromParent() {
			if (logger.isTraceEnabled()) {
				logger.trace("Closing of application context {} and removing it from parent {} started", applicationContextId,
						(parentContext != null) ? parentContext.applicationContextId : "no parent");
			}
			boolean closed = _close();
			if (closed && parentContext != null) {
				parentContext.childContexts.remove(this.applicationContextId);
			}
		}

		public ApplicationContext getChildContext(String applicationContextId) {
			return childContexts.get(applicationContextId);
		}

		public boolean containsChildContextKey(String applicationContextId) {
			return childContexts.containsKey(applicationContextId);
		}

		public void putChildContext(String applicationContextId, ApplicationContext applicationContext) {
			childContexts.put(applicationContextId, applicationContext);
		}

		private void buildClassLoader(ApplicationContext parentContext) throws FileManagerException {
			ClassLoader classLoader = descriptor.buildClassLoader(parentContext.classLoader);
			if (logger.isTraceEnabled()) {
				logger.trace("Loading classloader for {} in application context builder {}", descriptor.getId(), this);
			}
			this.classLoader = classLoader;
			if (logger.isTraceEnabled()) {
				logger.trace("Loaded classloader {}", classLoader);
			}
		}

		/**
		 * Method used by descriptor with descriptor.requiresReload() returning true, no implementation exists as of now
		 * Legacy implementation was building a new class loader, replacing the current one and clearing the contextObject map
		 * With new implementation, it now close first the current classloader and close the contextObject
		 * the method aims to recreate the class loader which now required to clean up the previous
		 * @param descriptor
		 * @param parentContext
		 * @throws FileManagerException
		 */
		public void reloadContext(ApplicationContextFactory descriptor, ApplicationContext parentContext) throws FileManagerException {
			//Start by cleaning previous context
			close();
			this.descriptor = descriptor;
			ClassLoader classLoader = descriptor.buildClassLoader(parentContext.classLoader);
			if (logger.isDebugEnabled()) {
				logger.debug("Loading classloader for {} in application context builder {}", descriptor.getId(), this);
			}
			this.classLoader = classLoader;
			closeContextObjects();
			contextObjects.clear();
		}

		public boolean _close() {
			boolean result;
			if (logger.isDebugEnabled()) {
				logger.debug("Starting cleanup of application context {}", applicationContextId);
			}
			int currentUsage = usage.get();
			if (currentUsage != 0) {
				logger.error("Cleanup requested while the application context {} is still in use, usage count: {}", applicationContextId, currentUsage);
				result = false;
			} else if (!childContexts.isEmpty()) {
				logger.error("Cleanup requested while the application context still has child context still in use, usage count: {}, child contexts: {}", currentUsage, childContexts);
				result = false;
			} else if (descriptor != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("closing classloader {} for app context {}", classLoader, applicationContextId);
				}
				closeClassLoader();
				if (logger.isDebugEnabled()) {
					logger.debug("Notifying application context factory");
				}
				descriptor.onClassLoaderClosed();
				result = true;
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("Cleanup requested for the application context {}. Classloader was provided to the context by its creator, nothing to do.", applicationContextId);
				}
				result = true;
			}
			if (result) {
				closeContextObjects();
			}
			return result;
		}

		private void closeContextObjects() {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing application context objects...");
			}
			contextObjects.values().forEach(ContextObjectWrapper::close);
		}

		public boolean close() {
			//Close recursively all children, before closing itself
			childContexts.entrySet().removeIf(childAppContextEntry -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Closing application child context {}", childAppContextEntry.getKey());
				}
				return childAppContextEntry.getValue().close();
			});
			return _close();
		}

		public boolean cleanup(long cleanupTime) {
			//Clean up recursively all children, before closing itself if eligible for cleanup
			childContexts.entrySet().removeIf(childAppContextEntry -> {
				if (logger.isDebugEnabled()) {
					logger.debug("Cleaning application child context {} if eligible", childAppContextEntry.getKey());
				}
				return childAppContextEntry.getValue().cleanup(cleanupTime);
			});
			if (childContexts.isEmpty() && usage.get() == 0 && (cleanupTTLMilliseconds == 0 || (cleanupTime - lastUsage) > cleanupTTLMilliseconds)) {
				return _close();
			} else {
				return false;
			}
		}

		private void closeClassLoader() {
			if (classLoader != null && classLoader instanceof AutoCloseable) {
				if (logger.isTraceEnabled()) {
					logger.trace("Application context class loader found for {}, closing classLoader {}", this, classLoader);
					logger.trace("Parent classloader is {}", classLoader.getParent());
					if (classLoader instanceof URLClassLoader) {
						URLClassLoader classLoader1 = (URLClassLoader) classLoader;
						logger.trace("URLs: {}", Arrays.asList(classLoader1.getURLs()));
					}
				}
				try {
					((AutoCloseable) classLoader).close();
				} catch (Exception e) {
					logger.error("Application context class loader found could not be closed for context {}", this, e);
				}
			}
		}

		private class ContextObjectWrapper implements AutoCloseable {

			private final Object object;
			private final boolean closeWithContext;

			public ContextObjectWrapper(Object object, boolean closeWithContext) {
				this.object = object;
				this.closeWithContext = closeWithContext;
			}

			@Override
			public void close() {
				if(closeWithContext && object instanceof AutoCloseable) {
					try {
						((AutoCloseable) object).close();
					} catch (Exception e) {
						logger.error("Error while closing context object", e);
					}
				}
			}
		}
	}


	/**
	 * Creates a new instance of {@link ApplicationContextBuilder} using the {@link ClassLoader} 
	 * of this class as root {@link ClassLoader}
	 */
	public ApplicationContextBuilder(ExecutionContextCacheConfiguration executionContextCacheConfiguration) {
		this(ApplicationContextBuilder.class.getClassLoader(), executionContextCacheConfiguration);
	}
	
	/**
	 * Creates a new instance of {@link ApplicationContextBuilder} using the specified {@link ClassLoader} 
	 * 
	 * @param rootClassLoader the {@link ClassLoader} to be used as root of this context
	 */
	public ApplicationContextBuilder(ClassLoader rootClassLoader, ExecutionContextCacheConfiguration executionContextCacheConfiguration) {
		ApplicationContext rootContext = new ApplicationContext(rootClassLoader, "rootContext");
		Branch branch = new Branch(rootContext, MASTER);
		branches.put(MASTER, branch);
		this.executionContextCacheConfiguration = executionContextCacheConfiguration;
		//We're using here the time unit from configuration which is Minutes by default but can be overridden for junit test
		this.cleanupTTLMilliseconds = executionContextCacheConfiguration.getConfigurationTimeUnit().toMillis(executionContextCacheConfiguration.getCleanupTimeToLiveMinutes());
		scheduleCleanupJob();
	}

	public ExecutionContextCacheConfiguration getApplicationContextConfiguration() {
		return executionContextCacheConfiguration;
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
	public ApplicationContextControl pushContext(ApplicationContextFactory descriptor, boolean cleanable) throws ApplicationContextBuilderException {
		return pushContext(MASTER, descriptor, cleanable);
	}

	/**
	 * Push a new context (resulting in a {@link ClassLoader}) to the stack of the branch specified as input for the current thread
	 * 
	 * @param branchName the name of the branch to push the new context onto
	 * @param descriptor the descriptor of the context to be pushed
	 * @param cleanable whether this app context and underlying class loader are cleanble
	 * @throws ApplicationContextBuilderException
	 */
	public ApplicationContextControl pushContext(String branchName, ApplicationContextFactory descriptor, boolean cleanable) throws ApplicationContextBuilderException {
		synchronized(this) {
			String contextKey = descriptor.getId();
			if(logger.isTraceEnabled()) {
                logger.trace("Pushing context {} to branch {}", contextKey, branchName);
			}
 			ThreadLocal<ApplicationContext> branchCurrentContext = getBranch(branchName).getCurrentContexts();
			ApplicationContext parentContext = branchCurrentContext.get();
			if(parentContext==null) {
				throw new RuntimeException("The current context is null. This should never occur");
			}
			
			ApplicationContext context;
			if(!parentContext.containsChildContextKey(contextKey)) {
				if(logger.isTraceEnabled()) {
                    logger.trace("Context {} doesn't exist on branch {}. Creating new context", contextKey, branchName);
				}

				try {
					context = new ApplicationContext(descriptor, parentContext, contextKey, cleanable);
				} catch (FileManagerException e) {
					throw new ApplicationContextBuilderException(e);
				}
				parentContext.putChildContext(contextKey, context);
			} else {
				if(logger.isTraceEnabled()) {
                    logger.trace("Context {} existing on branch. Reusing it.", contextKey);
				}
				context = parentContext.getChildContext(contextKey);
				try {
					if(descriptor.requiresReload()) {
						if(logger.isTraceEnabled()) {
                            logger.trace("Context {} requires reload. Reloading...", contextKey);
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

	@Override
	public void close() {
		if (logger.isDebugEnabled()) {
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
		//The full tree of application contexts can be browsed (and closed) from the master branch root context
		getBranch(MASTER).close();
	}

	protected void cleanup(long cleanupTime) {
		synchronized(this) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cleaning up all application contexts");
			}
			//The full tree of application context can be browsed from the master branch root context
			getBranch(MASTER).cleanup(cleanupTime);
		}
	}

	/**
	 * Schedule the cleanup job of application context with the frequency defined with {@link ExecutionContextCacheConfiguration#getCleanupFrequencyMinutes()}.
	 * <p>It can be disabled using the {@link ExecutionContextCacheConfiguration#isEnableCleanup()} flag.</p>
	 * <p>The cleanup job browses all entries from the cache and remove the one marked as cleanable and not accessed for the period of time defined with {@link ExecutionContextCacheConfiguration#getCleanupTimeToLiveMinutes()}</p>
	 *
	 */
	protected void scheduleCleanupJob() {
		if (executionContextCacheConfiguration.isEnableCleanup()) {
			long cleanupIntervalMinutes = executionContextCacheConfiguration.getCleanupFrequencyMinutes();
			logger.info("Scheduling execution context cache cleanup with a TTL of {} minutes and a frequency of {} minutes", executionContextCacheConfiguration.getCleanupTimeToLiveMinutes(), cleanupIntervalMinutes);
			NamedThreadFactory factory = new NamedThreadFactory("application-context-builder-cleanup-pool-" + UUID.randomUUID());
			scheduledPool = Executors.newScheduledThreadPool(1, factory);
			future = scheduledPool.scheduleAtFixedRate(() -> {
				try {
					if (logger.isDebugEnabled()) {
						logger.debug("Executing application context cleanup job for builder {}", this);
					}
					cleanup(System.currentTimeMillis());
				} catch (Throwable e) {
					logger.error("Unhandled error while running the application context builder clean up task.", e);
				}
			}, cleanupIntervalMinutes, cleanupIntervalMinutes, executionContextCacheConfiguration.getConfigurationTimeUnit());
		} else {
			logger.info("Execution context cache cleanup is disabled");
		}
	}
}
