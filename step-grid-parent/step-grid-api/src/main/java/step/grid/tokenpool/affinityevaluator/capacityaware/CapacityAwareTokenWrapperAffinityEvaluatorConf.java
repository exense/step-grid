package step.grid.tokenpool.affinityevaluator.capacityaware;

import java.util.ArrayList;
import java.util.List;

public class CapacityAwareTokenWrapperAffinityEvaluatorConf {

	protected List<Agent> agents = new ArrayList<>();

	public List<Agent> getAgents() {
		return agents;
	}

	public void setAgents(List<Agent> agents) {
		this.agents = agents;
	}

	public static class Agent {

		protected String hostname;

		protected int capacity;

		public String getHostname() {
			return hostname;
		}

		public void setHostname(String hostname) {
			this.hostname = hostname;
		}

		public int getCapacity() {
			return capacity;
		}

		public void setCapacity(int capacity) {
			this.capacity = capacity;
		}
	}
}
