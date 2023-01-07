package tech.quantit.northstar.gateway.api.domain.contract;

import tech.quantit.northstar.common.constant.ChannelType;
import tech.quantit.northstar.common.constant.Constants;
import tech.quantit.northstar.common.model.Identifier;
import tech.quantit.northstar.gateway.api.domain.time.TradeTimeDefinition;
import xyz.redtorch.pb.CoreEnum.ExchangeEnum;
import xyz.redtorch.pb.CoreEnum.ProductClassEnum;
import xyz.redtorch.pb.CoreField.ContractField;

/**
 * 主力合约
 * @author KevinHuangwl
 *
 */
public class PrimaryContract implements Contract {
	
	private IndexContract idxContract;
	
	public PrimaryContract(IndexContract idxContract) {
		this.idxContract = idxContract;
	}

	@Override
	public boolean subscribe() {
		return idxContract.subscribe();
	}

	@Override
	public boolean unsubscribe() {
		return idxContract.unsubscribe();
	}

	@Override
	public boolean hasSubscribed() {
		return idxContract.hasSubscribed();
	}

	@Override
	public String name() {
		return idxContract.name().replace("指数", "主力");
	}

	@Override
	public Identifier identifier() {
		return Identifier.of(idxContract.identifier().value().replace(Constants.INDEX_SUFFIX, Constants.PRIMARY_SUFFIX));
	}

	@Override
	public ProductClassEnum productClass() {
		return idxContract.productClass();
	}

	@Override
	public ExchangeEnum exchange() {
		return idxContract.exchange();
	}

	@Override
	public TradeTimeDefinition tradeTimeDefinition() {
		return idxContract.tradeTimeDefinition();
	}

	@Override
	public ChannelType channelType() {
		return ChannelType.PLAYBACK;
	}

	@Override
	public String gatewayId() {
		return idxContract.gatewayId();
	}

	@Override
	public ContractField contractField() {
		ContractField idxcf = idxContract.contractField();
		return ContractField.newBuilder(idxcf)
				.setName(name())
				.setFullName(idxcf.getFullName().replace("指数", "主力"))
				.setSymbol(idxcf.getSymbol().replace(Constants.INDEX_SUFFIX, Constants.PRIMARY_SUFFIX))
				.setUnifiedSymbol(idxcf.getUnifiedSymbol().replace(Constants.INDEX_SUFFIX, Constants.PRIMARY_SUFFIX))
				.setContractId(idxcf.getContractId().replace(Constants.INDEX_SUFFIX, Constants.PRIMARY_SUFFIX))
				.setThirdPartyId(idxcf.getThirdPartyId().replace(Constants.INDEX_SUFFIX, Constants.PRIMARY_SUFFIX))
				.build();
	}

}
