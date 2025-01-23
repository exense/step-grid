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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import junit.framework.Assert;
import step.grid.agent.tokenpool.TokenReservationSession;
import step.grid.agent.tokenpool.UnusableTokenReservationSession;
import step.grid.filemanager.FileManagerException;
import step.grid.contextbuilder.ApplicationContextBuilder.ApplicationContext;

public class ApplicationContextBuilderTest {

	protected AtomicInteger onCloseCalls = new AtomicInteger(0);

	@Test
	public void test() throws ApplicationContextBuilderException, MalformedURLException {
		testWithSession(new TokenReservationSession());
	}
	@Test
	public void testWithUnusableSession() throws ApplicationContextBuilderException, MalformedURLException {
		testWithSession(new UnusableTokenReservationSession());
	}


	private void testWithSession(TokenReservationSession tokenReservationSession) throws ApplicationContextBuilderException, MalformedURLException {
		ClassLoader rootClassloader = Thread.currentThread().getContextClassLoader();

		TestApplicationContextBuilder builder = new TestApplicationContextBuilder();
		builder.forkCurrentContext("branch2");

		builder.resetContext();

		tokenReservationSession.closeWithSession(builder.pushContext(newApplicationContextFactory("file://context1")));

		builder.forkCurrentContext("branch3");

		tokenReservationSession.closeWithSession(builder.pushContext("branch2", newApplicationContextFactory("file://context3")));

		tokenReservationSession.closeWithSession(builder.pushContext("branch3", newApplicationContextFactory("file://context4")));

		tokenReservationSession.closeWithSession(builder.pushContext(newApplicationContextFactory("file://context2")));
		builder.getCurrentContext().put("myKey", "myValue");

		ApplicationContext context = builder.getCurrentContext();

		Assert.assertEquals(new URL("file://context2"), ((URLClassLoader)context.getClassLoader()).getURLs()[0]);
		ClassLoader classloaderContext2 = context.getClassLoader();
		ClassLoader classloaderContext1 = classloaderContext2.getParent();
		Assert.assertEquals(new URL("file://context1"), ((URLClassLoader)classloaderContext1).getURLs()[0]);
		Assert.assertEquals("myValue", context.get("myKey"));

		ApplicationContext contextBranch2 = builder.getCurrentContext("branch2");
		Assert.assertEquals(new URL("file://context3"), ((URLClassLoader)contextBranch2.getClassLoader()).getURLs()[0]);
		Assert.assertEquals(rootClassloader, contextBranch2.getClassLoader().getParent());

		ApplicationContext contextBranch3 = builder.getCurrentContext("branch3");
		Assert.assertEquals(new URL("file://context4"), ((URLClassLoader)contextBranch3.getClassLoader()).getURLs()[0]);
		Assert.assertEquals(new URL("file://context1"), ((URLClassLoader)contextBranch3.getClassLoader().getParent()).getURLs()[0]);

		builder.resetContext();
		Assert.assertEquals(rootClassloader, builder.getCurrentContext().getClassLoader());
		Assert.assertEquals(rootClassloader, builder.getCurrentContext("branch2").getClassLoader());
		Assert.assertEquals(new URL("file://context1"), ((URLClassLoader)builder.getCurrentContext("branch3").getClassLoader()).getURLs()[0]);

		tokenReservationSession.closeWithSession(builder.pushContext(newApplicationContextFactory("file://context1")));
		// Assert that the classloader hasn't been created again i.e. that the classloader created during the first
		// push of the context1 has been reused
		Assert.assertTrue(classloaderContext1 == builder.getCurrentContext().getClassLoader());
		tokenReservationSession.closeWithSession(builder.pushContext(newApplicationContextFactory("file://context2")));
		// Assert the same for the context2
		Assert.assertTrue(classloaderContext2 == builder.getCurrentContext().getClassLoader());

		//4 context are created, (2 are reused once), so 6 app context controls have to be closed
		Assert.assertEquals(6, builder.appCtrl.size());
		tokenReservationSession.close();

		builder.appCtrl.forEach(a -> {
			Assert.assertTrue(a.isClosed);
		});

		//4 context are created, (2 are reused once)
		Assert.assertEquals(4, onCloseCalls.get());
	}

	private ApplicationContextFactory newApplicationContextFactory(String id) {
		return new ApplicationContextFactory() {
			
			@Override
			public boolean requiresReload() throws FileManagerException {
				return false;
			}
			
			@Override
			public String getId() {
				return id;
			}
			
			@Override
			public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileManagerException {
				try {
					return new URLClassLoader(new URL[]{new URL(id)}, parentClassLoader);
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onClassLoaderClosed() {
				onCloseCalls.incrementAndGet();
			}
		};
	}

	public static class TestApplicationContextBuilder extends ApplicationContextBuilder {

		public final List<TestApplicationContextControl> appCtrl = new LinkedList<>();

		@Override
		public ApplicationContextControl pushContext(String branchName, ApplicationContextFactory descriptor) throws ApplicationContextBuilderException {
			TestApplicationContextControl applicationContextControl = new TestApplicationContextControl(super.pushContext(branchName, descriptor));
			appCtrl.add(applicationContextControl);
			return (ApplicationContextControl) applicationContextControl;
		}

		public static class TestApplicationContextControl extends ApplicationContextControl {

			public boolean isClosed = false;

			public TestApplicationContextControl(ApplicationContext applicationContext) {
				super(applicationContext);
			}

			public TestApplicationContextControl(ApplicationContextControl applicationContextControl) {
				super(applicationContextControl.applicationContext);
			}

			@Override
			public void close() throws Exception {
				super.close();
				isClosed = true;
			}
		}
	}

}
