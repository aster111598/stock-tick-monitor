package com.quant.monitor.network;

import com.quant.monitor.model.Tick;
import com.quant.monitor.parser.TickParser;
import com.quant.monitor.service.MarketDataService;
import com.quant.monitor.util.FugleParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 行情接收處理器 繼承 TextWebSocketHandler，專門處理純文字 (JSON) 的逐筆報價串流
 */
@Component
public class QuoteWebSocketHandler extends TextWebSocketHandler {

	@Value("${fugle.api.key}")
	private String fugleApiKey;

	// 引入企業級日誌，絕對不要在盤中使用 System.out.println 拖垮效能
	private static final Logger log = LoggerFactory.getLogger(QuoteWebSocketHandler.class);

	private final TickParser tickParser;
	private final MarketDataService marketDataService;

	// 透過建構子注入我們剛寫好的解析器
	public QuoteWebSocketHandler(TickParser tickParser, MarketDataService marketDataService) {
		this.tickParser = tickParser;
		this.marketDataService = marketDataService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		// 1. 先進行身分驗證 (依照你貼的文件格式)
		// 註：這裡建議直接從 application.properties 讀取 API Key，或暫時硬編碼測試
		String apiKey = fugleApiKey;

		String authMsg = String.format("""
				{
				  "event": "auth",
				  "data": {
				    "apikey": "%s"
				  }
				}
				""", apiKey);

		session.sendMessage(new TextMessage(authMsg));
		System.out.println("🔑 已發送身分驗證請求...");

		// 2. 依照官方文件訂閱 Trades 頻道 (成交明細)
		// 支援一次訂閱多檔：2330 (台積電), 2454 (聯發科), 2317 (鴻海), 3711 (日月光投控)
		String subscribeMsg = """
				{
				  "event": "subscribe",
				  "data": {
				    "channel": "trades",
				    "symbols": ["2330", "2454", "2317",	"3711"]
				  }
				}
				""";

		session.sendMessage(new TextMessage(subscribeMsg));
		System.out.println("🚀 已送出官方標準訂閱請求：台積電、聯發科、鴻海、日月光投控");
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		String payload = message.getPayload();

		// 🔍 觀察原始數據（建議開盤前保留，確認格式）
		System.out.println("📥 收到原始數據: " + payload);

		// 1. 使用升級後的 Parser，它會自動從 JSON 裡抓出對應的 symbol
		Tick tick = FugleParser.parse(payload);

		// 2. 只有當解析出有效的 Tick 時，才餵入演算法大腦
		if (tick != null) {
			// 這裡的 tick.symbol() 會是 "2454" 或 "2317" 等，由 Parser 自動判定
			marketDataService.onTickReceived(tick);
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.error("⚠️ WebSocket 傳輸發生異常: {}", exception.getMessage());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		log.warn("🔴 券商連線已中斷! 狀態碼: {}", status.getCode());
		// 這裡是一個極關鍵的「防禦壓力位」，未來必須在這裡實作「斷線自動重連機制」
	}
}