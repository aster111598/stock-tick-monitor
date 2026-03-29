package com.quant.monitor.network;

import com.quant.monitor.model.Tick;
import com.quant.monitor.parser.TickParser;
import com.quant.monitor.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Optional;

/**
 * WebSocket 行情接收處理器
 * 繼承 TextWebSocketHandler，專門處理純文字 (JSON) 的逐筆報價串流
 */
@Component
public class QuoteWebSocketHandler extends TextWebSocketHandler {

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
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("🟢 成功連線至券商 WebSocket API! Session ID: {}", session.getId());
        // 實戰中，這裡通常會立刻發送一個「訂閱特定股票 (如 2330)」的 JSON 請求給券商
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 1. 攔截到券商傳來的 JSON 字串
        String payload = message.getPayload();
        
        // 2. 交給大腦進行光速解析
        Optional<Tick> tickOpt = tickParser.parse(payload);
        
        // 3. 【核心樞紐】只要解析成功，立刻把 Tick 丟給大腦進行高頻累加與多空判斷
        // 這裡使用了 Java 8 的方法參照 (Method Reference)，語法極度乾淨
        tickOpt.ifPresent(marketDataService::onTickReceived);
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