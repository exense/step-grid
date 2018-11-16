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
package step.grid;

import step.grid.tokenpool.Identity;
import step.grid.tokenpool.SimpleAffinityEvaluator;

public class TokenWrapperAffinityEvaluatorImpl extends SimpleAffinityEvaluator<Identity, TokenWrapper> {

	public int getAffinityScore(Identity i1, TokenWrapper i2) {
		if(i2.getState().equals(TokenWrapperState.ERROR)||i2.getState().equals(TokenWrapperState.MAINTENANCE)) {
			return -1;
		} else {
			return super.getAffinityScore(i1, i2);
		}
	}
}
