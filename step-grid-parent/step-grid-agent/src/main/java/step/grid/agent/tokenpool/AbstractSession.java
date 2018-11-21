/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
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
 *******************************************************************************/
package step.grid.agent.tokenpool;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractSession implements Closeable {

	private static final Logger logger = LoggerFactory.getLogger(AbstractSession.class);
	
	protected Map<String, Object> sessionObjects = new HashMap<>();

	public AbstractSession() {
		super();
	}

	public Object get(String arg0) {
		return sessionObjects.get(arg0);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T get(Class<T> objectClass) {
		Object object = get(objectClass.getName());
		if(object!=null) {
			if(objectClass.isAssignableFrom(object.getClass())) {
				return (T) object;
			} else {
				throw new RuntimeException("The object found is not an instance of "+objectClass.getName());
			}
		} else {
			return null;
		}
	}

	public void put(Object object) {
		put(object.getClass().getName(), object);
	}
	
	public Object put(String arg0, Object arg1) {
		Object previous = get(arg0);
		closeIfCloseable(arg0, previous);
		return sessionObjects.put(arg0, arg1);
	}

	private void closeIfCloseable(String arg0, Object previous) {
		if(previous!=null && previous instanceof Closeable) {
			logger.debug("Attempted to replace session object with key '"+arg0+"'. Closing previous object.");
			try {
				((Closeable)previous).close();
			} catch (Exception e) {
				logger.error("Error while closing '"+arg0+"' from session.",e);
			}
		}
	}

	public void close() {
		for(String key:sessionObjects.keySet()) {
			closeIfCloseable(key, sessionObjects.get(key));
		}
	}

}