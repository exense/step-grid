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