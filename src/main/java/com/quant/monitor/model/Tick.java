package com.quant.monitor.model;

import java.time.LocalTime;

/**
 * 逐筆交易 (Tick) 資料載體
 * 使用 Java 21 Record 建立不可變 (Immutable) 物件，確保高頻多執行緒下的絕對安全
 */
public record Tick(
    String symbol,        // 股票代號 (如 "2330")
    LocalTime time,       // 交易所傳來的成交時間 (如 09:05:12.123)
    double price,         // 成交價
    int volume,           // 單筆成交張數
    int bsFlag            // 買賣力道標記 (1: 外盤主動買, -1: 內盤主動賣, 0: 中立)
) {
    
    // 實體內部可以直接封裝你最在意的「大小單」與「多空」客觀判斷邏輯
    
    /**
     * 判斷是否為大單
     * @param volumeThreshold 自訂的大單門檻 (例如 50 張)
     */
    public boolean isLargeOrder(int volumeThreshold) {
        return this.volume >= volumeThreshold;
    }

    /**
     * 是否為主動買盤 (外盤)
     */
    public boolean isBuyPower() {
        return this.bsFlag == 1;
    }

    /**
     * 是否為主動賣盤 (內盤)
     */
    public boolean isSellPower() {
        return this.bsFlag == -1;
    }
}