package step.grid;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import step.grid.tokenpool.Interest;

public interface Grid extends GridFileService {

	public static String LOCAL_AGENT = "local";

	TokenWrapper selectToken(Map<String, String> attributes, Map<String, Interest> interests, long matchTimeout, long noMatchTimeout, TokenWrapperOwner tokenOwner) throws TimeoutException, InterruptedException;

	void returnToken(String id);

	List<TokenWrapper> getTokens();
	
	void markTokenAsFailing(String tokenId, String errorMessage, Exception e);
}