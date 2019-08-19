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
package step.grid.tokenpool.affinityevaluator;

import step.grid.TokenWrapper;
import step.grid.TokenWrapperState;
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
