/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
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
