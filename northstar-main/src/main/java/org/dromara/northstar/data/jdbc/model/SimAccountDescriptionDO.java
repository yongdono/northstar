package org.dromara.northstar.data.jdbc.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import org.dromara.northstar.common.model.SimAccountDescription;

import com.alibaba.fastjson2.JSON;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name="SIM_ACCOUNT", indexes = {
		@Index(name="idx_gatewayId", columnList = "gatewayId")
})
public class SimAccountDescriptionDO {

	@Id
	private String gatewayId;
	
	private String dataStr;
	
	public static SimAccountDescriptionDO convertFrom(SimAccountDescription simAccountDescription) {
		return new SimAccountDescriptionDO(simAccountDescription.getGatewayId(), JSON.toJSONString(simAccountDescription));
	}
	
	public SimAccountDescription convertTo() {
		return JSON.parseObject(dataStr, SimAccountDescription.class);
	}
}
