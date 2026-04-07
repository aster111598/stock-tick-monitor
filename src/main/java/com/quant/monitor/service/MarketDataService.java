package com.quant.monitor.service;

import com.quant.monitor.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 實時籌碼運算大腦 (V3.0 效能終極版) 
 * 導入讀寫分離、定時巡邏機制，並整合工業級非同步分流日誌 (SLF4J + Logback)
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final int LARGE_ORDER_THRESHOLD = 50;
    private final ConcurrentHashMap<String, MarketMetrics> metricsMap = new ConcurrentHashMap<>();

    /**
     * 【高頻寫入區】只做 O(1) 的極速狀態機更新，絕不卡死 WebSocket 執行緒
     */
    public void onTickReceived(Tick tick) {
        String symbol = tick.symbol();
        
        // 🔍 1. 日常水流 (DEBUG 等級)
        // 作用：只在 Console 顯示「瀑布流」，不會被 XML 寫入 alert.log，保護硬碟空間。
        log.debug("📥 成交快訊 | 標的: {} | 價格: {} | 量: {} | 內外盤: {}", 
                  symbol, tick.price(), tick.volume(), tick.bsFlag());

        MarketMetrics metrics = metricsMap.computeIfAbsent(symbol, key -> new MarketMetrics(key));

        // 更新底層籌碼與狀態機
        metrics.processTick(tick, LARGE_ORDER_THRESHOLD);

        // 狀態機是 O(1) 運算，極度輕量，可以直接在這裡判斷並示警
        if (metrics.detectAlgoSmallSells(8)) {
            // 🚨 2. 核心警報 (WARN 等級)
            // 作用：會被 logback-spring.xml 攔截，非同步寫入 logs/alert.log 永久保存。
            log.warn("⚠️ [狀態機示警] {}: 偵測到連續 8 筆內盤小單隱蔽賣出！觸發現價: {}", symbol, tick.price());
        }
        
        if (metrics.detectAlgoSmallBuys(8)) {
            // 📈 3. 一般資訊 (INFO 等級)
            // 作用：顯示在 Console，讓你知道有主力在吃貨。
            log.info("📈 [狀態機示警] {}: 偵測到連續 8 筆外盤小單隱蔽吃貨！觸發現價: {}", symbol, tick.price());
        }
    }

    /**
     * 【低頻重度運算區】每 5000 毫秒 (5 秒) 自動執行一次
     * 負責掃描所有監控標的，計算耗 CPU 的 5 分鐘時間窗動能斜率
     */
    @Scheduled(fixedRate = 5000)
    public void evaluateTimeWindowStrategies() {
        // 如果目前沒有任何股票資料，直接跳過不浪費資源
        if (metricsMap.isEmpty()) return;

        LocalTime now = LocalTime.now();

        // 走訪所有正在監控的股票，進行微觀斜率評估
        metricsMap.values().forEach(metrics -> {

            // 這裡就是原本會拖垮效能的 O(N) 時間分桶迴圈
            boolean isAmplifying = metrics.detectAmplifyingSmallSells(now, LARGE_ORDER_THRESHOLD);

            if (isAmplifying) {
                // 🚨 4. 最高警報 (ERROR 等級)
                // 作用：強烈的主力倒貨訊號，必定寫入 alert.log。
                log.error("🚨 [動能破底] {}: 過去 5 分鐘內盤程式單賣壓斜率急遽放大！主力可能正在倒貨。", metrics.getSymbol());
            }
        });
    }
}