package step.grid.contextbuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

import junit.framework.Assert;
import step.grid.contextbuilder.ApplicationContextBuilder.ApplicationContext;
import step.grid.filemanager.FileProviderException;

public class ApplicationContextBuilderTest {

	@Test
	public void test() throws ApplicationContextBuilderException, MalformedURLException {
		ClassLoader rootClassloader = Thread.currentThread().getContextClassLoader();
		
		ApplicationContextBuilder builder = new ApplicationContextBuilder();
		builder.forkCurrentContext("branch2");
		
		builder.resetContext();
		
		builder.pushContext(newApplicationContextFactory("file://context1"));
		
		builder.forkCurrentContext("branch3");
		
		builder.pushContext("branch2", newApplicationContextFactory("file://context3"));
		
		builder.pushContext("branch3", newApplicationContextFactory("file://context4"));
		
		builder.pushContext(newApplicationContextFactory("file://context2"));
		builder.getCurrentContext().put("myKey", "myValue");
		
		ApplicationContext context = builder.getCurrentContext();
				
		Assert.assertEquals(new URL("file://context2"), ((URLClassLoader)context.getClassLoader()).getURLs()[0]);
		Assert.assertEquals(new URL("file://context1"), ((URLClassLoader)context.getClassLoader().getParent()).getURLs()[0]);
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
	}

	private ApplicationContextFactory newApplicationContextFactory(String id) {
		return new ApplicationContextFactory() {
			
			@Override
			public boolean requiresReload() throws FileProviderException {
				return false;
			}
			
			@Override
			public String getId() {
				return id;
			}
			
			@Override
			public ClassLoader buildClassLoader(ClassLoader parentClassLoader) throws FileProviderException {
				try {
					return new URLClassLoader(new URL[]{new URL(id)}, parentClassLoader);
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

}
