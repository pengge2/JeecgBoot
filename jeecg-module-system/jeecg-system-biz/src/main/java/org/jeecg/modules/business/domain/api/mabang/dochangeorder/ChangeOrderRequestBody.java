package org.jeecg.modules.business.domain.api.mabang.dochangeorder;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.jeecg.modules.business.domain.api.mabang.RequestBody;

import java.util.HashSet;
import java.util.function.Function;

public class ChangeOrderRequestBody implements RequestBody {

    @Setter
    @Getter
    private String platformOrderId;
    private String orderStatus;
    private String remark;

    private final HashSet<Pair<String, Integer>> oldSkuData;

    private final HashSet<Pair<String, Integer>> newSkuData;

    private final static String DEFAULT_WAREHOUSE_NAME = "SZBA宝安仓";

    public enum OperationType {
        MODIFY(1),
        REMOVE(2),
        ADD(3);
        private final Integer code;

        OperationType(Integer code) {
            this.code = code;
        }
    }

    public ChangeOrderRequestBody(String platformOrderId, String orderStatus, HashSet<Pair<String, Integer>> oldSkuData,
                                  HashSet<Pair<String, Integer>> newSkuData, String remark) {
        this.platformOrderId = platformOrderId;
        this.oldSkuData = oldSkuData;
        this.newSkuData = newSkuData;
        this.orderStatus = orderStatus;
        this.remark = remark;
    }

    @Override
    public String api() {
        return "order-do-change-order";
    }

    @Override
    public JSONObject parameters() {
        JSONObject json = new JSONObject();
        putNonNull(json, "platformOrderId", platformOrderId);
        putNonNull(json, "orderStatus", orderStatus);
        putNonNull(json, "remark", remark);
        JSONArray stockDataArray = new JSONArray();
        if (oldSkuData != null && !oldSkuData.isEmpty()) {
            for (Pair<String, Integer> oldSkuDatum : oldSkuData) {
                JSONObject stockData = new JSONObject();
                stockData.put("warehouseName", DEFAULT_WAREHOUSE_NAME);
                stockData.put("stockSku", oldSkuDatum.getKey());
                stockData.put("quantity", oldSkuDatum.getValue());
                stockData.put("type", OperationType.REMOVE.code);
                stockDataArray.add(stockData);
            }

        }
        if(newSkuData != null) {
            for (Pair<String, Integer> newSkuDatum : newSkuData) {
                JSONObject stockData = new JSONObject();
                stockData.put("warehouseName", DEFAULT_WAREHOUSE_NAME);
                stockData.put("stockSku", newSkuDatum.getKey());
                stockData.put("quantity", newSkuDatum.getValue());
                stockData.put("type", OperationType.ADD.code);
                stockDataArray.add(stockData);
            }
        }
        putNonNull(json,"stockData", stockDataArray.toJSONString());
        return json;
    }

    private <E> void putNonNull(JSONObject json, String key, E value) {
        if (value != null) {
            json.put(key, value);
        }
    }

    private <E, T> void putNonNull(JSONObject json, String key, E value, Function<E, T> mapper) {
        if (value != null) {
            json.put(key, mapper.apply(value));
        }
    }


}