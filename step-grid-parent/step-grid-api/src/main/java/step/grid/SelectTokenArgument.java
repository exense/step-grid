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
package step.grid;

import java.util.Map;

import step.grid.tokenpool.Interest;

public class SelectTokenArgument {
	
	protected Map<String, String> attributes;
	protected Map<String, Interest> interests;
	protected long matchTimeout;
	protected long noMatchTimeout;
	protected TokenWrapperOwner tokenOwner;
	
	public SelectTokenArgument() {
		super();
	}

	public SelectTokenArgument(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout,
			long noMatchTimeout, TokenWrapperOwner tokenOwner) {
		super();
		this.attributes = attributes;
		this.interests = interests;
		this.matchTimeout = matchTimeout;
		this.noMatchTimeout = noMatchTimeout;
		this.tokenOwner = tokenOwner;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public Map<String, Interest> getInterests() {
		return interests;
	}

	public long getMatchTimeout() {
		return matchTimeout;
	}

	public long getNoMatchTimeout() {
		return noMatchTimeout;
	}

	public void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes;
	}

	public void setInterests(Map<String, Interest> interests) {
		this.interests = interests;
	}

	public void setMatchTimeout(long matchTimeout) {
		this.matchTimeout = matchTimeout;
	}

	public void setNoMatchTimeout(long noMatchTimeout) {
		this.noMatchTimeout = noMatchTimeout;
	}

	public TokenWrapperOwner getTokenOwner() {
		return tokenOwner;
	}

	public void setTokenOwner(TokenWrapperOwner tokenOwner) {
		this.tokenOwner = tokenOwner;
	}
}
