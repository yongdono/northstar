package tech.quantit.northstar.common.constant;

public enum ChannelType {
	
	SIM {
		@Override
		public GatewayUsage[] usage() {
			return new GatewayUsage[] {GatewayUsage.MARKET_DATA, GatewayUsage.TRADE};
		}
	},
	
	CTP {
		@Override
		public GatewayUsage[] usage() {
			return new GatewayUsage[] {GatewayUsage.MARKET_DATA, GatewayUsage.TRADE};
		}
	},
	
	CTP_SIM {
		@Override
		public GatewayUsage[] usage() {
			return new GatewayUsage[] {GatewayUsage.MARKET_DATA, GatewayUsage.TRADE};
		}

		@Override
		public boolean adminOnly() {
			return true;
		}
	},
	
	PLAYBACK {
		@Override
		public GatewayUsage[] usage() {
			return new GatewayUsage[] {GatewayUsage.MARKET_DATA};
		}

		@Override
		public boolean allowDuplication() {
			return true;
		}
	};

	public abstract GatewayUsage[] usage();

	
	public boolean adminOnly() {
		return false;
	}

	public boolean allowDuplication() {
		return false;
	}
	
}
