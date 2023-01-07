package tech.quantit.northstar.gateway.api;

import java.util.Collection;
import java.util.Map;

import tech.quantit.northstar.common.constant.ChannelType;
import tech.quantit.northstar.common.model.ComponentField;
import tech.quantit.northstar.common.model.DynamicParams;
import tech.quantit.northstar.common.model.GatewaySettings;

public class GatewayMetaProvider {
	
	/* gatewayType -> settings */
	Map<ChannelType, GatewaySettings> settingsMap;
	
	Map<ChannelType, GatewayFactory> factoryMap;
	
	public GatewayMetaProvider(Map<ChannelType, GatewaySettings> settingsMap, Map<ChannelType, GatewayFactory> factoryMap) {
		this.settingsMap = settingsMap;
		this.factoryMap = factoryMap;
	}
	
	public Collection<ComponentField> getSettings(ChannelType channelType) {
		return  ((DynamicParams)settingsMap.get(channelType)).getMetaInfo().values();
	}
	
	public GatewayFactory getFactory(ChannelType gatewayType) {
		if(!factoryMap.containsKey(gatewayType)) {
			throw new IllegalStateException("不存在该网关类型：" + gatewayType);
		}	
		return factoryMap.get(gatewayType);
	}
}
