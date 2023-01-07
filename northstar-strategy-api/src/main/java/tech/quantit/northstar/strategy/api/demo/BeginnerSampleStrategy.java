package tech.quantit.northstar.strategy.api.demo;

import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;

import com.alibaba.fastjson.JSONObject;

import tech.quantit.northstar.common.constant.FieldType;
import tech.quantit.northstar.common.constant.ModuleState;
import tech.quantit.northstar.common.constant.SignalOperation;
import tech.quantit.northstar.common.model.DynamicParams;
import tech.quantit.northstar.common.model.Setting;
import tech.quantit.northstar.strategy.api.IModuleContext;
import tech.quantit.northstar.strategy.api.IModuleStrategyContext;
import tech.quantit.northstar.strategy.api.TradeStrategy;
import tech.quantit.northstar.strategy.api.annotation.StrategicComponent;
import tech.quantit.northstar.strategy.api.constant.PriceType;
import tech.quantit.northstar.strategy.api.utils.time.TickBasedTimer;
import tech.quantit.northstar.strategy.api.utils.trade.TradeIntent;
import xyz.redtorch.pb.CoreField.BarField;
import xyz.redtorch.pb.CoreField.OrderField;
import xyz.redtorch.pb.CoreField.TickField;
import xyz.redtorch.pb.CoreField.TradeField;

/**
 * 本示例用于展示写一个策略的必要元素，以及最基本的开平仓操作、超时撤单操作
 * 
 * ## 风险提示：该策略仅作技术分享，据此交易，风险自担 ##
 * @author KevinHuangwl
 *
 */
@StrategicComponent(BeginnerSampleStrategy.NAME)		// 该注解是用于给策略命名用的，所有的策略都要带上这个注解
public class BeginnerSampleStrategy implements TradeStrategy{
	
	protected static final String NAME = "示例-简单策略";	// 之所以要这样定义一个常量，是为了方便日志输出时可以带上策略名称
	
	private InitParams params;	// 策略的参数配置信息
	
	private IModuleStrategyContext ctx;		// 模组的操作上下文
	
	private JSONObject inspectableState = new JSONObject(); 	// 可透视状态计算信息
	
	private Logger log;
	
	/**
	 * 定义该策略的参数。该类每个策略必须自己重写一个，类名必须为InitParams，必须继承DynamicParams，必须是个static类。
	 * @author KevinHuangwl
	 */
	public static class InitParams extends DynamicParams {			// 每个策略都要有一个用于定义初始化参数的内部类，类名称不能改
		
		@Setting(label="操作间隔", type = FieldType.NUMBER, order=10, unit="秒")		// Label注解用于定义属性的元信息。可以声明单位
		private int actionInterval;						// 属性可以为任意多个，当元素为多个时order值用于控制前端的显示顺序
		
	}
	
	/***************** 以下如果看不懂，基本可以照搬 *************************/
	@Override
	public DynamicParams getDynamicParams() {
		return new InitParams();
	}

	@Override
	public void initWithParams(DynamicParams params) {
		this.params = (InitParams) params;
	}
	
	@Override
	public void setContext(IModuleContext context) {
		ctx = context;
		log = ctx.getLogger();
	}
	
	@Override
	public JSONObject getComputedState() {
		return inspectableState;
	}

	@Override
	public void setComputedState(JSONObject stateObj) {
		this.inspectableState = stateObj;
	}
	/***************** 以上如果看不懂，基本可以照搬 *************************/
	
	// 从88行至112行 这是自行管理撤单的逻辑。还可以通过下单时使用TradeIntent，直接定义下单意图，让框架来处理撤单追单逻辑，例如138行与148行示例。
	private TickBasedTimer timer = new TickBasedTimer();
	private TimerTask runningTask = null;
	private TimerTask withdrawOrderIfTimeout = new TimerTask() {
		@Override
		public void run() {
			originOrderId.ifPresent(ctx::cancelOrder);
		}
	};
	
	@Override
	public void onOrder(OrderField order) {
		if(runningTask == null && ctx.getState().isWaiting()) {
			runningTask = withdrawOrderIfTimeout;
			timer.schedule(runningTask, 5000);		// 5秒超时撤单
		}
	}

	@Override
	public void onTrade(TradeField trade) {
		originOrderId.ifPresent(id -> {			
			if(trade.getOriginOrderId().equals(id)) {
				runningTask = null;
			}
		});
	}
	
	private long nextActionTime;
	private Optional<String> originOrderId = Optional.empty();
	
	@Override
	public void onTick(TickField tick) {
		timer.onTick(tick);
		
		log.debug("TICK触发: C:{} D:{} T:{} P:{} V:{} OI:{} OID:{}", 
				tick.getUnifiedSymbol(), tick.getActionDay(), tick.getActionTime(), 
				tick.getLastPrice(), tick.getVolume(), tick.getOpenInterest(), tick.getOpenInterestDelta());
		long now = tick.getActionTimestamp();
		// 启用后，等待10秒才开始交易
		if(nextActionTime == 0) {
			nextActionTime = now + 10000;
		}
		boolean flag = ThreadLocalRandom.current().nextBoolean();
		if(now > nextActionTime) {
			nextActionTime = now + params.actionInterval * 1000;
			log.info("开始交易");
			if(ctx.getState().isEmpty()) {
				SignalOperation op = flag ? SignalOperation.BUY_OPEN : SignalOperation.SELL_OPEN;	// 随机开多或者开空
				originOrderId = ctx.submitOrderReq(ctx.getContract(tick.getUnifiedSymbol()), op, PriceType.WAITING_PRICE, 1, tick.getLastPrice());
			}
			if(ctx.getState() == ModuleState.HOLDING_LONG) {	
				ctx.submitOrderReq(TradeIntent.builder()
						.context(ctx)
						.contract(ctx.getContract(tick.getUnifiedSymbol()))
						.operation(SignalOperation.SELL_CLOSE)
						.priceType(PriceType.WAITING_PRICE)
						.volume(1)
						.timeout(3000)
						.build());
			}
			if(ctx.getState() == ModuleState.HOLDING_SHORT) {			
				ctx.submitOrderReq(TradeIntent.builder()
						.context(ctx)
						.contract(ctx.getContract(tick.getUnifiedSymbol()))
						.operation(SignalOperation.BUY_CLOSE)
						.priceType(PriceType.WAITING_PRICE)
						.volume(1)
						.timeout(3000)
						.build());
			}
		}
	}

	@Override
	public void onMergedBar(BarField bar) {
		log.debug("策略每分钟触发");
	}

}
