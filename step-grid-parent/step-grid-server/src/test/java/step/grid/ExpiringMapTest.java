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
package step.grid;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class ExpiringMapTest {

	@Test
	public void test() throws InterruptedException {
		// The keepalive timeout should be higher than the longer stop the world that is to be expected for
		// the JVM running this test. A too short keep alive would cause this test to fail a line 54 while calling the 
		// get method as the object might have been deleted during the stop the world
		ExpiringMap<String, String> m = new ExpiringMap<>(500, 10);
		
		m.put("test", "test");
		m.put("test2", "test");
		
		
		Assert.assertEquals("test", m.get("test"));
		Thread.sleep(700);;
		
		Assert.assertNull(m.get("test"));
		
		m.put("test", "test");
		Assert.assertEquals("test", m.get("test"));
		
		// Loop longer than 500ms
		for(int i=0;i<700;i++) {
			m.touch("test");
			Thread.sleep(1);
		}
		
		Assert.assertEquals("test", m.get("test"));
		
		
		m.remove("test");
		Assert.assertNull(m.get("test"));
		Assert.assertFalse(m.containsKey("test"));
	}
	
	@Test
	public void testEntrySet() throws InterruptedException {
		ExpiringMap<String, String> m = new ExpiringMap<>(1000, 1);
		
		m.put("test", "value");
		m.put("test2", "value");
		
		Set<Entry<String, String>> entrySet = m.entrySet();
		Assert.assertEquals(2, entrySet.size());
		entrySet.forEach(e->{
			assert e.getKey().equals("test") || e.getKey().equals("test2");
			assert e.getValue().equals("value");
		});
		
		m.clear();
		Assert.assertEquals(0, m.size());
		Assert.assertEquals(0, m.entrySet().size());
		
		m.put("test", "value");
		Collection<String> values = m.values();
		// expected:<1> but was:<0>
		Assert.assertEquals(1, values.size());
		values.contains("value");
	}
}
