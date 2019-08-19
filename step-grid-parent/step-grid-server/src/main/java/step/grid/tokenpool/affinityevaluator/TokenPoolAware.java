package step.grid.tokenpool.affinityevaluator;

import step.grid.tokenpool.TokenPool;

public interface TokenPoolAware {

	public void setTokenPool(TokenPool<?,?> tokenPool);
}
