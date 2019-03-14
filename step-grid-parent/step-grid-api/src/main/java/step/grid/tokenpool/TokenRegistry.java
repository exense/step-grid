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
package step.grid.tokenpool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import step.grid.TokenWrapper;


public interface TokenRegistry {

	TokenWrapper selectToken(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout, long noMatchTimeout) throws TimeoutException, InterruptedException;

	void returnToken(TokenWrapper object);

	List<step.grid.tokenpool.Token<TokenWrapper>> getTokens();
	
	void markTokenAsFailing(String tokenId, String errorMessage, Exception e);

}