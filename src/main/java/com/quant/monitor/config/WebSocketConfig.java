package com.quant.monitor.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.quant.monitor.network.QuoteWebSocketHandler;

/**
 * WebSocket 連線設定與啟動中心
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	@Autowired
	private QuoteWebSocketHandler quoteWebSocketHandler;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		// 這裡維持原樣，註冊本地的 endpoint
	}

	@Bean
	public WebSocketConnectionManager webSocketConnectionManager() {
		StandardWebSocketClient client = new StandardWebSocketClient();
		String streamingUrl = "wss://api.fugle.tw/marketdata/v1.0/stock/streaming";

		WebSocketConnectionManager manager = new WebSocketConnectionManager(client, quoteWebSocketHandler,
				streamingUrl);

		manager.setAutoStartup(true);
		return manager;
	}
}