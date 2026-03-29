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
 * 導入讀寫分離與定時巡邏機制，徹底消滅高頻阻塞風險
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
        MarketMetrics metrics = metricsMap.computeIfAbsent(
            tick.symbol(), 
            key -> new MarketMetrics(key)
        );

        // 更新底層籌碼與狀態機
        metrics.processTick(tick, LARGE_ORDER_THRESHOLD);

        // 狀態機是 O(1) 運算，極度輕量，可以直接在這裡判斷並示警
        if (metrics.detectAlgoSmallSells(8)) {
            log.warn("⚠️ [狀態機示警] {}: 偵測到連續 8 筆內盤小單隱蔽賣出！", metrics.getSymbol());
        }
        if (metrics.detectAlgoSmallBuys(8)) {
            log.info("📈 [狀態機示警] {}: 偵測到連續 8 筆外盤小單隱蔽吃貨！", metrics.getSymbol());
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
                log.error("🚨 [動能破底] {}: 過去 5 分鐘內盤程式單賣壓斜率急遽放大！主力可能正在倒貨。", metrics.getSymbol());
            }
        });
    }
}