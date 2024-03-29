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
package step.grid.client.reports;

import step.grid.AgentRef;
import step.grid.TokenWrapper;
import step.grid.TokenWrapperOwner;
import step.grid.client.GridClient;
import step.grid.tokenpool.Interest;

import java.util.*;

public class GridReportBuilder {

	private GridClient gridClient;
	
	public GridReportBuilder(GridClient gridClient) {
		super();
		this.gridClient = gridClient;
	}

	public List<TokenGroupCapacity> getUsageByIdentity(List<String> groupbys) {		
		Map<Map<String, String>, TokenGroupCapacity> countsByIdentity = new HashMap<>();
		
		for(TokenWrapper aToken: gridClient.getTokens()) {
			Map<String, String> key = new HashMap<>(); 

			for(String groupby:groupbys) {
				key.put(groupby, getValue(groupby, aToken));
			}
			
			if(!countsByIdentity.containsKey(key)) {
				countsByIdentity.put(key, new TokenGroupCapacity(key));
			}
			TokenGroupCapacity c = countsByIdentity.get(key);
			c.incrementCapacity();
			c.incrementUsage(aToken.getState());
		}
		
		return new ArrayList<>(countsByIdentity.values());
	}
	
	public Set<String> getTokenAttributeKeys() {
		Set<String> result = new HashSet<>();
		for(TokenWrapper token: gridClient.getTokens()) {
			result.addAll(token.getAttributes().keySet());
			if(token.getInterests()!=null) {
				result.addAll(token.getInterests().keySet());				
			}
		}
		return result;
	}
	
	private static final String UID_KEY = "id";
	
	private static final String URL_KEY = "url";
	
	private String getValue(String key, TokenWrapper aToken) {
		if(key.equals(UID_KEY)) {
			return aToken.getID();
		}
		if(key.equals(URL_KEY)) {
			AgentRef ref = aToken.getAgent();
			return ref!=null?ref.getAgentUrl():"-";
		}
		if(aToken.getAttributes()!=null) {
			String attribute = aToken.getAttributes().get(key);
			if(attribute!=null) {
				return attribute;						
			}
		}
		if(aToken.getInterests()!=null) {
			Interest interest = aToken.getInterests().get(key);
			if(interest!=null) {
				return interest.getSelectionPattern().toString();	
			}
		}
		return null;
	}
	
	public List<TokenWrapper> getTokenAssociations(boolean onlyWithOwner) {
		List<TokenWrapper> tokens = new ArrayList<>();
		for(TokenWrapper token: gridClient.getTokens()) {
			TokenWrapperOwner currentOwner = token.getCurrentOwner();
			if(currentOwner!=null||(currentOwner==null&&!onlyWithOwner)) {
				tokens.add(token);
			}
		}
		return tokens;
	}
}
