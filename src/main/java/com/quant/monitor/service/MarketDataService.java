package com.quant.monitor.service;

import com.quant.monitor.model.MarketMetrics;
import com.quant.monitor.model.Tick;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 實時籌碼運算大腦 (V4.0 企業級觀測版)
 * 整合 Micrometer 埋點，將核心吞吐量、警報觸發次數與運算延遲輸出至 Prometheus
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final int LARGE_ORDER_THRESHOLD = 50;
    private final ConcurrentHashMap<String, MarketMetrics> metricsMap = new ConcurrentHashMap<>();

    // --- 📊 Micrometer 監控感測器宣告 ---
    private final Counter ticksParsedCounter;
    private final Counter alertSellCounter;
    private final Counter alertBuyCounter;
    private final Timer evaluationTimer;

    // 透過 Spring Boot 自動注入 MeterRegistry
    public MarketDataService(MeterRegistry registry) {
        // 1. 成功解析計數器 (用來對比 WebSocket 收到的總量，抓出靜默丟棄的數量)
        this.ticksParsedCounter = registry.counter("stock.ticks.parsed.success", "source", "fugle");
        
        // 2. 警報計數器 (附帶 tags，讓 Grafana 可以畫出買賣力道對比的圓餅圖)
        this.alertSellCounter = registry.counter("stock.algo.alerts.total", "type", "sell_dump");
        this.alertBuyCounter = registry.counter("stock.algo.alerts.total", "type", "buy_accumulate");
        
        // 3. 延遲計時器 (監控 O(N) 迴圈會不會造成系統塞車)
        this.evaluationTimer = registry.timer("stock.evaluation.latency");
    }

    /**
     * 【高頻寫入區】 O(1) 極速狀態機
     */
    public void onTickReceived(Tick tick) {
        if (tick == null) return; // 防禦性檢查

        // 📍 埋點：紀錄成功進入演算法的一筆有效資料
        ticksParsedCounter.increment();

        String symbol = tick.symbol();
        log.debug("📥 成交快訊 | 標的: {} | 價格: {} | 量: {} | 內外盤: {}", 
                  symbol, tick.price(), tick.volume(), tick.bsFlag());

        MarketMetrics metrics = metricsMap.computeIfAbsent(symbol, key -> new MarketMetrics(key));
        metrics.processTick(tick, LARGE_ORDER_THRESHOLD);

        if (metrics.detectAlgoSmallSells(8)) {
            log.warn("⚠️ [狀態機示警] {}: 偵測到連續 8 筆內盤小單隱蔽賣出！觸發現價: {}", symbol, tick.price());
            // 📍 埋點：紀錄倒貨警報觸發
            alertSellCounter.increment(); 
        }
        
        if (metrics.detectAlgoSmallBuys(8)) {
            log.info("📈 [狀態機示警] {}: 偵測到連續 8 筆外盤小單隱蔽吃貨！觸發現價: {}", symbol, tick.price());
            // 📍 埋點：紀錄吃貨警報觸發
            alertBuyCounter.increment();  
        }
    }

    /**
     * 【低頻重度運算區】每 5000 毫秒掃描一次時間窗動能
     */
    @Scheduled(fixedRate = 5000)
    public void evaluateTimeWindowStrategies() {
        if (metricsMap.isEmpty()) return;

        // 📍 埋點：使用 evaluationTimer.record 包裝原本的邏輯，精準測量這段程式碼跑了幾毫秒
        evaluationTimer.record(() -> {
            LocalTime now = LocalTime.now();
            
            metricsMap.values().forEach(metrics -> {
                boolean isAmplifying = metrics.detectAmplifyingSmallSells(now, LARGE_ORDER_THRESHOLD);
                if (isAmplifying) {
                    log.error("🚨 [動能破底] {}: 過去 5 分鐘內盤程式單賣壓斜率急遽放大！主力可能正在倒貨。", metrics.getSymbol());
                }
            });
        });
    }
}