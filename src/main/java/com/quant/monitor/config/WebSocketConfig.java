package com.quant.monitor.config;

import com.quant.monitor.network.QuoteWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/**
 * WebSocket 連線設定與啟動中心
 */
@Configuration
public class WebSocketConfig {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    // 這裡先放一個測試用的公開 WebSocket Echo Server (它會把你傳的字串原封不動傳回來)
    // 等到你申請好真實的券商 API，再把這個 URL 換掉
    private static final String BROKER_WSS_URL = "wss://echo.websocket.org";

    @Bean
    public WebSocketConnectionManager connectionManager(QuoteWebSocketHandler quoteHandler) {
        log.info("啟動 WebSocket 連線管理器，準備連接至: {}", BROKER_WSS_URL);
        
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                client, 
                quoteHandler, 
                BROKER_WSS_URL
        );
        
        // 設定為 Spring Boot 啟動時自動連線
        manager.setAutoStartup(true);
        return manager;
    }
}