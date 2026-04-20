package com.quant.monitor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.monitor.model.Tick;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;

public class FugleParser {
	private static final ObjectMapper mapper = new ObjectMapper();

	public static Tick parse(String json) {
		try {
			JsonNode root = mapper.readTree(json);
			String event = root.path("event").asText();

			// 我們只處理 'trade' (盤中成交) 與 'snapshot' (開盤前的最後狀態)
			if ("trade".equals(event) || "snapshot".equals(event)) {
				JsonNode data = root.get("data");
				
				// 🛡️ 防禦性檢查：缺少關鍵欄位直接放棄，不拋出 Exception
	            if (!data.has("symbol") || !data.has("price") || !data.has("size")) {
	                return null; // 直接忽略
	            }

				String symbol = data.get("symbol").asText();
				double price = data.get("price").asDouble();
				int volume = data.get("size").asInt();

				// 處理時間 (富果使用微秒，轉為 LocalTime)
				long micros = data.get("time").asLong();
				LocalTime time = Instant.ofEpochMilli(micros / 1000).atZone(ZoneId.systemDefault()).toLocalTime();

				// 判斷內外盤 (bsFlag):
				// 快照通常沒提供 side，我們可以預設為 0； trade 則會有 side 欄位
				int bsFlag = 0;
				if (data.has("side")) {
					bsFlag = "B".equals(data.get("side").asText()) ? 1 : -1;
				}

				return new Tick(symbol, time, price, volume, bsFlag);
			}
		} catch (Exception e) {
			// 解析失敗通常是因為收到系統訊息 (如 authenticated)，直接跳過即可
		}
		return null;
	}
}