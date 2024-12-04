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

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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

	protected class Branch {
		
		private ApplicationContext rootContext;
		
		private final ThreadLocal<ApplicationContext> currentContexts = new ThreadLocal<>();

		protected Branch(ApplicationContext rootContext) {
			super();
			this.rootContext = rootContext;
			reset();
		}
		
		protected void reset() {
			currentContexts.set(rootContext);
		}
		
		public Branch fork(String newBranchName) {
			ApplicationContext currentContext = currentContexts.get();
			Branch newBranch = new Branch(currentContext);
			ApplicationContextBuilder.this.branches.put(newBranchName, newBranch);
			return newBranch;
		}

		protected ThreadLocal<ApplicationContext> getCurrentContexts() {
			return currentContexts;
		}
	}
	
	public static class ApplicationContext implements Closeable {
		
		private ClassLoader classLoader;

		private Map<String, ApplicationContext> childContexts = new ConcurrentHashMap<>();
		
		private ConcurrentHashMap<String, Object> contextObjects = new ConcurrentHashMap<>();

		protected ApplicationContext() {
			super();
		}
		
		protected ApplicationContext(ClassLoader classLoader) {
			super();
			this.classLoader = classLoader;
		}

		public Object get(Object key) {
			return contextObjects.get(key);
		}

		public Object computeIfAbsent(String key, Function<? super String, Object> mappingFunction) {
			return contextObjects.computeIfAbsent(key, mappingFunction);
		}

		public Object put(String key, Object value) {
			return contextObjects.put(key, value);
		}

		public ClassLoader getClassLoader() {
			return classLoader;
		}
		
		@Override
		public void close() throws IOException {
			if(logger.isDebugEnabled()) {
				logger.debug("Closing application context, starting with all children");
			}
			childContexts.forEach((k, a) -> {
                try {
                    a.close();
                } catch (IOException e) {
					logger.error("Unable to close the child application context with key {}.", k, e);
                }
            });
			if(logger.isDebugEnabled()) {
				logger.debug("Closing application context associated classloader if any");
			}
			if(classLoader!=null && classLoader instanceof Closeable) {
				if(logger.isDebugEnabled()) {
					logger.debug("Application context class loader found, closing it");
				}
				((Closeable)classLoader).close();
			}
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
		ApplicationContext rootContext = new ApplicationContext(rootClassLoader);
		Branch branch = new Branch(rootContext);
		branches.put(MASTER, branch);
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
	public void pushContext(ApplicationContextFactory descriptor) throws ApplicationContextBuilderException {
		pushContext(MASTER, descriptor);
	}
	
	/**
	 * Push a new context (resulting in a {@link ClassLoader}) to the stack of the branch specified as input for the current thread
	 * 
	 * @param branchName the name of the branch to push the new context onto
	 * @param descriptor the descriptor of the context to be pushed 
	 * @throws ApplicationContextBuilderException
	 */
	public void pushContext(String branchName, ApplicationContextFactory descriptor) throws ApplicationContextBuilderException {
		synchronized(this) {
			String contextKey = descriptor.getId();
			if(logger.isDebugEnabled()) {
				logger.debug("Pushing context "+contextKey+" to branch "+branchName);
			}
 			ThreadLocal<ApplicationContext> branch = getBranch(branchName).getCurrentContexts();
			ApplicationContext parentContext = branch.get();
			if(parentContext==null) {
				throw new RuntimeException("The current context is null. This should never occur");
			}
			
			ApplicationContext context;
			if(!parentContext.childContexts.containsKey(contextKey)) {
				if(logger.isDebugEnabled()) {
					logger.debug("Context "+contextKey+" doesn't exist on branch "+branchName+". Creating new context");
				}
				context = new ApplicationContext();
				try {
					buildClassLoader(descriptor, context, parentContext);
				} catch (FileManagerException e) {
					throw new ApplicationContextBuilderException(e);
				}
				parentContext.childContexts.put(contextKey, context);
			} else {
				if(logger.isDebugEnabled()) {
					logger.debug("Context "+contextKey+" existing on branch. Reusing it.");
				}
				context = parentContext.childContexts.get(contextKey);	
				try {
					if(descriptor.requiresReload()) {
						if(logger.isDebugEnabled()) {
							logger.debug("Context "+contextKey+" requires reload. Reloading...");
						}
						buildClassLoader(descriptor, context, parentContext);
						context.contextObjects.clear();
					} else {
						
					}
				} catch (FileManagerException e) {
					throw new ApplicationContextBuilderException(e);
				}
			}
			branch.set(context);
		}
	}

	private void buildClassLoader(ApplicationContextFactory descriptor, ApplicationContext context,	ApplicationContext parentContext) throws FileManagerException {
		ClassLoader classLoader = descriptor.buildClassLoader(parentContext.classLoader);
		if(logger.isDebugEnabled()) {
			logger.debug("Loading classloader for "+descriptor.getId());
		}
		if (context.classLoader != null  && classLoader instanceof Closeable) {
			if(logger.isDebugEnabled()) {
				logger.debug("Previous classloader found, closing it");
			}
            try {
                ((Closeable) context.classLoader).close();
            } catch (IOException e) {
                logger.error("Unable to close the application context classloader for {}", descriptor.getId(), e);
            }
        }
		context.classLoader = classLoader;
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
		if(logger.isDebugEnabled()) {
			logger.debug("Closing all application contexts");
		}
		branches.forEach((k,b) -> {
			if(logger.isDebugEnabled()) {
				logger.debug("Closing application contexts for branch {}", k);
			}
			if (b.rootContext != null) {
				try {
					b.rootContext.close();
				} catch (IOException e) {
					logger.error("Unable to close the root application context of branch {}.", k, e);
				}
			}
			ThreadLocal<ApplicationContext> branch = b.getCurrentContexts();
			ApplicationContext parentContext = branch.get();
			if (parentContext != null) {
				try {
					parentContext.close();
				} catch (IOException e) {
					logger.error("Unable to close the current thread application context of branch {}.", k, e);
				}
			}
		});
	}
}
