package tech.xuanwu.northstar.strategy.common.model;

import java.util.Optional;
import java.util.UUID;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tech.xuanwu.northstar.strategy.common.model.entity.ModuleDealRecord;
import xyz.redtorch.pb.CoreEnum.ContingentConditionEnum;
import xyz.redtorch.pb.CoreEnum.DirectionEnum;
import xyz.redtorch.pb.CoreEnum.ForceCloseReasonEnum;
import xyz.redtorch.pb.CoreEnum.HedgeFlagEnum;
import xyz.redtorch.pb.CoreEnum.OffsetFlagEnum;
import xyz.redtorch.pb.CoreEnum.OrderPriceTypeEnum;
import xyz.redtorch.pb.CoreEnum.PositionDirectionEnum;
import xyz.redtorch.pb.CoreEnum.TimeConditionEnum;
import xyz.redtorch.pb.CoreEnum.VolumeConditionEnum;
import xyz.redtorch.pb.CoreField.ContractField;
import xyz.redtorch.pb.CoreField.OrderField;
import xyz.redtorch.pb.CoreField.SubmitOrderReqField;
import xyz.redtorch.pb.CoreField.TickField;
import xyz.redtorch.pb.CoreField.TradeField;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Slf4j
public class ModulePosition {

	@NotNull
	private String unifiedSymbol;
	@NotNull
	private PositionDirectionEnum positionDir;
	
	private String openTradingDay;
	
	private long openTime;
	
	private double multiplier;
	@NotNull
	@Min(value=1, message="开仓价格应该为正数")
	private double openPrice;
	@Min(value=0, message="止损价不能为负数")
	private double stopLossPrice;
	@NotNull
	@Min(value=1, message="手数应该为大于零的整数")
	private int volume;
	@Transient
	private double holdingProfit;
	@Transient
	private long lastTriggerTime;
	
	public ModulePosition(TradeField trade, OrderField order) {
		if(!StringUtils.equals(trade.getOriginOrderId(), order.getOriginOrderId())) {
			log.warn("委托明细：{}", order.toString());
			log.warn("成交明细：{}", trade.toString());
			throw new IllegalStateException("成交与委托不匹配");
		}
		this.unifiedSymbol = trade.getContract().getUnifiedSymbol();
		this.positionDir = trade.getDirection() == DirectionEnum.D_Buy ? PositionDirectionEnum.PD_Long : PositionDirectionEnum.PD_Short;
		this.openPrice = trade.getPrice();
		this.stopLossPrice = order.getStopPrice();
		this.volume = trade.getVolume();
		this.multiplier = trade.getContract().getMultiplier();
		this.openTradingDay = trade.getTradingDay();
		this.openTime = trade.getTradeTimestamp();
	}
	
	public double updateProfit(TickField tick) {
		checkMatch(tick.getUnifiedSymbol());
		int factor = positionDir == PositionDirectionEnum.PD_Long ? 1 : -1;
		holdingProfit = factor * (tick.getLastPrice() - openPrice) * volume * multiplier;
		return holdingProfit;
	}
	
	public Optional<SubmitOrderReqField> triggerStopLoss(TickField tick, ContractField contract) {
		checkMatch(tick.getUnifiedSymbol());
		if(stopLossPrice <= 0) {
			return Optional.empty();
		}
		long now = tick.getActionTimestamp();
		// 如果三秒后该持仓还没被清理，证明市价挂单无成交，则需要再次发单
		if(now - lastTriggerTime > 3000 && triggeredStopLoss(tick)) {
			lastTriggerTime = now;
			SubmitOrderReqField orderReq = SubmitOrderReqField.newBuilder()
					.setOriginOrderId(UUID.randomUUID().toString())
					.setContract(contract)
					.setDirection(getClosingDirection())
					.setVolume(volume)
					.setPrice(0) 											//市价专用
					.setOrderPriceType(OrderPriceTypeEnum.OPT_AnyPrice)	//市价专用
					.setTimeCondition(TimeConditionEnum.TC_IOC)				//市价专用
					.setOffsetFlag(StringUtils.equals(tick.getTradingDay(), openTradingDay) ? OffsetFlagEnum.OF_CloseToday : OffsetFlagEnum.OF_CloseYesterday)
					.setVolumeCondition(VolumeConditionEnum.VC_AV)
					.setContingentCondition(ContingentConditionEnum.CC_Immediately)
					.setHedgeFlag(HedgeFlagEnum.HF_Speculation)
					.setForceCloseReason(ForceCloseReasonEnum.FCR_NotForceClose)
					.build();
			log.info("生成止损单：{}，{}，{}，{}手", orderReq.getContract().getSymbol(), orderReq.getDirection(), orderReq.getOffsetFlag(), orderReq.getVolume());
			return Optional.of(orderReq);
		}
		return Optional.empty();
	}
	
	private DirectionEnum getClosingDirection() {
		return positionDir == PositionDirectionEnum.PD_Long ? DirectionEnum.D_Sell : DirectionEnum.D_Buy;
	}
	
	private boolean triggeredStopLoss(TickField tick) {
		return positionDir == PositionDirectionEnum.PD_Long && tick.getLastPrice() <= stopLossPrice
				|| positionDir == PositionDirectionEnum.PD_Short && tick.getLastPrice() >= stopLossPrice;
	}
	
	public Optional<ModulePosition> onOpenTrade(TradeField trade) {
		checkMatch(trade.getContract().getUnifiedSymbol());
		if(OffsetFlagEnum.OF_Open != trade.getOffsetFlag()) {
			throw new IllegalStateException("传入了非开仓成交：" + trade.toString());
		}
		// 确保是同向开仓成交，忽略反向锁仓成交
		if(positionDir == PositionDirectionEnum.PD_Long && trade.getDirection() == DirectionEnum.D_Buy
				|| positionDir == PositionDirectionEnum.PD_Short && trade.getDirection() == DirectionEnum.D_Sell) {
			double originCost = openPrice * volume;
			double newCost = trade.getPrice() * trade.getVolume();
			openPrice = (originCost + newCost) / (volume + trade.getVolume());
			volume += trade.getVolume();
			return Optional.of(this);
		}
		return Optional.empty();
	}
	
	public Optional<ModuleDealRecord> onCloseTrade(TradeField trade){
		checkMatch(trade.getContract().getUnifiedSymbol());
		if(OffsetFlagEnum.OF_Open == trade.getOffsetFlag() || OffsetFlagEnum.OF_Unknown == trade.getOffsetFlag()) {
			throw new IllegalStateException("传入了非平仓成交：" + trade.toString());
		}
		// 确保是反向平仓
		if(positionDir == PositionDirectionEnum.PD_Long && trade.getDirection() == DirectionEnum.D_Sell
				|| positionDir == PositionDirectionEnum.PD_Short && trade.getDirection() == DirectionEnum.D_Buy) {
			if(volume < trade.getVolume()) {
				throw new IllegalStateException("成交数量大于持仓数量");
			}
			volume -= trade.getVolume();
			int factor = positionDir == PositionDirectionEnum.PD_Long ? 1 : -1;
			double closeProfit = factor * (trade.getPrice() - openPrice) * trade.getVolume() * multiplier;
			double occupiedMoney = Math.max(openPrice, trade.getPrice()) * trade.getVolume() * multiplier
					* (factor > 0 ? trade.getContract().getLongMarginRatio() : trade.getContract().getShortMarginRatio()) * 1.5;
			return Optional.of(ModuleDealRecord.builder()
					.contractName(trade.getContract().getSymbol())
					.direction(positionDir)
					.tradingDay(openTradingDay)
					.dealTimestamp(openTime)
					.openPrice(openPrice)
					.closePrice(trade.getPrice())
					.volume(trade.getVolume())
					.closeProfit((int)closeProfit)
					.estimatedOccupiedMoney(occupiedMoney)
					.build());
		}
		return Optional.empty();
	}
	
	public Optional<ModuleDealRecord> onStopLoss(SubmitOrderReqField orderReq, TickField tick){
		checkMatch(orderReq.getContract().getUnifiedSymbol());
		if(OffsetFlagEnum.OF_Open == orderReq.getOffsetFlag() || OffsetFlagEnum.OF_Unknown == orderReq.getOffsetFlag()) {
			throw new IllegalStateException("传入了非平仓成交：" + orderReq.toString());
		}
		if(orderReq.getOrderPriceType() == OrderPriceTypeEnum.OPT_AnyPrice) {
			int tradeVol = volume;
			volume = 0;
			int factor = positionDir == PositionDirectionEnum.PD_Long ? 1 : -1;
			double closeProfit = factor * (tick.getLastPrice() - openPrice) * tradeVol * multiplier;
			double occupiedMoney = tick.getLastPrice() * orderReq.getVolume() * multiplier
					* (factor > 0 ? orderReq.getContract().getLongMarginRatio() : orderReq.getContract().getShortMarginRatio()) * 1.5;
			return Optional.of(ModuleDealRecord.builder()
					.contractName(orderReq.getContract().getSymbol())
					.direction(positionDir)
					.tradingDay(openTradingDay)
					.dealTimestamp(openTime)
					.openPrice(openPrice)
					.closePrice(tick.getLastPrice())
					.volume(tradeVol)
					.closeProfit((int)closeProfit)
					.estimatedOccupiedMoney(occupiedMoney)
					.build());
		}
		return Optional.empty();
	}
	
	public boolean isLongPosition() {
		return positionDir == PositionDirectionEnum.PD_Long;
	}
	
	public boolean isShortPosition() {
		return positionDir == PositionDirectionEnum.PD_Short;
	}
	
	public String getUnifiedSymbol() {
		return unifiedSymbol;
	}
	
	public double getHoldingProfit() {
		return holdingProfit;
	}
	
	public boolean isEmpty() {
		return volume == 0;
	}
	
	public boolean isMatch(String unifiedSymbol) {
		return StringUtils.equals(unifiedSymbol, this.unifiedSymbol);
	}
	
	private void checkMatch(String unifiedSymbol) {
		if(!isMatch(unifiedSymbol)) {
			throw new IllegalStateException(this.unifiedSymbol + "与不匹配的数据更新：" + unifiedSymbol);
		}
	}
	
}
