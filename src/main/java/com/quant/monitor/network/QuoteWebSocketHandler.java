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
        log.info("🌐 WebSocket 連線已建立，準備發送身分驗證...");

        // 1. 建立連線時「只」發送身分驗證，絕對不偷跑訂閱請求！
        String authMsg = String.format("""
                {
                  "event": "auth",
                  "data": {
                    "apikey": "%s"
                  }
                }
                """, fugleApiKey);

        session.sendMessage(new TextMessage(authMsg));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // 建議盤中改為 log.debug，避免海量資料把磁碟空間塞爆
        log.debug("📥 收到原始數據: {}", payload);

        // 2. 攔截錯誤或驗證失敗的狀態
        if (payload.contains("Invalid authentication credentials") || payload.contains("Forbidden")) {
            log.error("❌ 富果 API 金鑰驗證失敗或被拒絕存取！詳細訊息: {}", payload);
            return; // 發生錯誤直接結束處理
        } 
        
        // 3. 攔截「驗證成功」事件，此時才安全地發出訂閱指令
        if (payload.contains("Authenticated successfully")) {
            log.info("✨ 身分驗證成功，開始發送股票訂閱指令！");
            
            // 依照官方文件訂閱 Trades 頻道 (成交明細)
            String subscribeMsg = """
                    {
                      "event": "subscribe",
                      "data": {
                        "channel": "trades",
                        "symbols": ["2330", "2454", "2317", "3711"]
                      }
                    }
                    """;

            session.sendMessage(new TextMessage(subscribeMsg));
            log.info("🚀 已送出訂閱請求：台積電、聯發科、鴻海、日月光投控");
            return; // 訂閱指令本身不是行情數據，不需要往下餵給 Parser，直接 return
        }

        // 4. 一般行情資料處理流程
        // 使用升級後的 Parser，自動從 JSON 裡抓出對應的 symbol
        Tick tick = FugleParser.parse(payload);

        // 只有當解析出有效的 Tick 時，才餵入演算法大腦
        if (tick != null) {
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