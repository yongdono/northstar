package tech.quantit.northstar.gateway.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import tech.quantit.northstar.common.constant.GatewayType;
import tech.quantit.northstar.common.event.FastEventEngine;
import tech.quantit.northstar.gateway.api.MarketDataBuffer;
import tech.quantit.northstar.gateway.api.MarketGateway;
import test.common.TestFieldFactory;
import java.util.function.Consumer;

import xyz.redtorch.pb.CoreField.ContractField;
import xyz.redtorch.pb.CoreField.TickField;

class GlobalMarketRegistryTest {
	
	TestFieldFactory factory = new TestFieldFactory("gateway");
	GlobalMarketRegistry registry;
	
	@Test
	void shouldSubscribeContract() {
		registry = new GlobalMarketRegistry(mock(FastEventEngine.class), mock(Consumer.class), mock(MarketDataBuffer.class));
		
		NormalContract contract = mock(NormalContract.class);
		MarketGateway gateway = mock(MarketGateway.class);
		when(gateway.isConnected()).thenReturn(true);
		when(contract.gatewayType()).thenReturn(GatewayType.CTP);
		when(contract.barGenerator()).thenReturn(mock(BarGenerator.class));
		registry.gatewayMap.put(GatewayType.CTP, gateway);
		Consumer<ContractField> callback = mock(Consumer.class);
		registry.setOnContractSubsciption(callback);
		registry.register(contract);
		
		verify(gateway).subscribe(any());
		verify(callback).accept(any());
	}
	
	@Test
	void shouldNotSubscribeContract() {
		registry = new GlobalMarketRegistry(mock(FastEventEngine.class), mock(Consumer.class), mock(MarketDataBuffer.class));
		
		IndexContract contract = mock(IndexContract.class);
		MarketGateway gateway = mock(MarketGateway.class);
		when(contract.indexTicker()).thenReturn(mock(IndexTicker.class));
		when(contract.gatewayType()).thenReturn(GatewayType.CTP);
		when(contract.barGenerator()).thenReturn(mock(BarGenerator.class));
		registry.gatewayMap.put(GatewayType.CTP, gateway);
		registry.register(contract);
		
		verify(gateway, times(0)).subscribe(any());
	}

	@Test
	void testRegisterSubscriptionManager() {
		registry = new GlobalMarketRegistry(mock(FastEventEngine.class), mock(Consumer.class), mock(MarketDataBuffer.class));
		SubscriptionManager subMgr = mock(SubscriptionManager.class);
		when(subMgr.usedFor()).thenReturn(GatewayType.CTP);
		registry.register(subMgr);
		assertThat(registry.subMgrMap).hasSize(1);
	}

	@Test
	void testRegisterMarketGateway() {
		registry = new GlobalMarketRegistry(mock(FastEventEngine.class), mock(Consumer.class), mock(MarketDataBuffer.class));
		registry.setOnContractSubsciption(mock(Consumer.class));
		NormalContract contract = mock(NormalContract.class);
		MarketGateway gateway = mock(MarketGateway.class);
		when(gateway.gatewayType()).thenReturn(GatewayType.CTP);
		when(gateway.isConnected()).thenReturn(true);
		when(contract.gatewayType()).thenReturn(GatewayType.CTP);
		when(contract.barGenerator()).thenReturn(mock(BarGenerator.class));
		registry.register(contract);
		registry.register(gateway);
		
		verify(gateway).subscribe(any());
	}

	@Test
	void testDispatch() {
		registry = new GlobalMarketRegistry(mock(FastEventEngine.class), mock(Consumer.class), mock(MarketDataBuffer.class));
		TickField tick = factory.makeTickField("rb2210", 2000);
		NormalContract contract = mock(NormalContract.class);
		MarketGateway gateway = mock(MarketGateway.class);
		when(gateway.gatewayType()).thenReturn(GatewayType.CTP);
		when(contract.gatewayType()).thenReturn(GatewayType.CTP);
		when(contract.unifiedSymbol()).thenReturn(tick.getUnifiedSymbol());
		BarGenerator barGen = mock(BarGenerator.class);
		when(contract.barGenerator()).thenReturn(barGen);
		registry.register(contract);
		registry.register(gateway);
		registry.dispatch(tick);
		
		verify(barGen).update(any());
	}

}
