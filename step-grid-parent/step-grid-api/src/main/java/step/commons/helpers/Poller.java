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
package step.commons.helpers;

import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class Poller {

	public static void waitFor(Supplier<Boolean> predicate, long timeout) throws TimeoutException, InterruptedException {
		long t1 = System.currentTimeMillis();
		while(timeout == 0 || System.currentTimeMillis()<t1+timeout) {
			boolean result = predicate.get();
			if(result) {
				return;
			}
			Thread.sleep(100);
		}
		throw new TimeoutException();
	}
}
