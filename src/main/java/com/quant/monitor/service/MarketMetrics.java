package com.quant.monitor.service;

import com.quant.monitor.model.Tick;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.atomic.LongAdder;

/**
 * 個股盤中籌碼聚合器 (V2.0 包含滾動時間窗與程式單偵測)
 */
public class MarketMetrics {
    
    private final String symbol;
    
    // 全日絕對累加器
    private final LongAdder totalVolume = new LongAdder();
    private final LongAdder outsideVolume = new LongAdder();
    private final LongAdder insideVolume = new LongAdder();
    private final LongAdder largeBuyVolume = new LongAdder();
    private final LongAdder largeSellVolume = new LongAdder();
    
    // 【新增】短期記憶中樞：5 分鐘滾動時間窗
    private final TimeWindowQueue recentTicks = new TimeWindowQueue(Duration.ofMinutes(5));

    // 【新增】狀態機：記錄連續小單的次數
    private int consecutiveSmallSells = 0;
    private int consecutiveSmallBuys = 0;

    public MarketMetrics(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 處理每一筆新進的 Tick，並更新狀態機
     */
    public void processTick(Tick tick, int largeOrderThreshold) {
        int vol = tick.volume();
        totalVolume.add(vol);

        // 1. 將資料放入短期記憶窗口，自動剔除 5 分鐘前的老舊資料
        recentTicks.addAndEvict(tick);

        boolean isLarge = tick.isLargeOrder(largeOrderThreshold);

        // 2. 更新全日累加與【連續程式單狀態機】
        if (tick.isBuyPower()) {
            outsideVolume.add(vol);
            if (isLarge) {
                largeBuyVolume.add(vol);
            }
            
            // 狀態機運算：遇到外盤主動買，中斷連續賣單的計數；增加買單計數
            if (!isLarge) {
                consecutiveSmallBuys++;
            } else {
                consecutiveSmallBuys = 0; // 大單介入，打斷程式小單的連續性
            }
            consecutiveSmallSells = 0; // 反向力道出現，賣單計數歸零

        } else if (tick.isSellPower()) {
            insideVolume.add(vol);
            if (isLarge) {
                largeSellVolume.add(vol);
            }
            
            // 狀態機運算：遇到內盤主動賣，中斷連續買單的計數；增加賣單計數
            if (!isLarge) {
                consecutiveSmallSells++;
            } else {
                consecutiveSmallSells = 0; // 大單介入，打斷程式小單的連續性
            }
            consecutiveSmallBuys = 0; // 反向力道出現，買單計數歸零
        }
    }
    
    /**
     * 【核心攻擊邏輯 V2.0】利用時間分桶偵測「賣壓慢慢放大」的隱蔽程式單
     * 完美過濾盤中零星散戶買單的雜訊干擾
     *
     * @param currentTime 目前系統時間
     * @param largeThreshold 大單門檻 (例如 50 張)
     * @return 是否確認為程式單惡意倒貨
     */
    public boolean detectAmplifyingSmallSells(LocalTime currentTime, int largeThreshold) {
        long t1SmallSellVol = 0; // 前半場：距今 2.5 ~ 5 分鐘的賣壓
        long t2SmallSellVol = 0; // 後半場：距今 0 ~ 2.5 分鐘的賣壓

        // 取得過去 5 分鐘的所有 Tick 快照進行微觀掃描
        for (Tick tick : recentTicks.getSnapshot()) {
            
            // 我們只專注於「內盤」且「小於大單門檻」的籌碼，這就是大戶演算法拆單的痕跡
            if (tick.isSellPower() && !tick.isLargeOrder(largeThreshold)) {
                
                // 計算這筆 Tick 距離現在過了幾秒
                long ageInSeconds = Duration.between(tick.time(), currentTime).getSeconds();
                
                if (ageInSeconds > 150) { 
                    // 大於 150 秒 (2.5 分鐘)，歸類為前半場
                    t1SmallSellVol += tick.volume();
                } else {
                    // 小於等於 150 秒，歸類為後半場
                    t2SmallSellVol += tick.volume();
                }
            }
        }

        // --- 客觀的防禦與觸發條件 ---
        // 條件 1 (斜率判斷)：後半場的程式小單賣出量，比前半場放大超過 50% (可視個股股性微調)
        boolean isSlopeSteep = t2SmallSellVol > (t1SmallSellVol * 1.5);
        
        // 條件 2 (流動性防禦)：確保後半場有絕對的賣壓實體 (例如大於 20 張)，避免 2 張變 4 張的低量假訊號
        boolean hasAbsoluteVolume = t2SmallSellVol > 20;

        return isSlopeSteep && hasAbsoluteVolume;
    }

    /**
     * 【核心攻擊邏輯】偵測「連續隱蔽倒貨」的程式單
     * 條件：連續出現 N 筆內盤小單，且完全沒有被外盤買單或大單中斷
     * * @param targetCount 觸發警示的連續筆數 (例如：連續 8 筆)
     * @return 是否觸發程式單賣出警示
     */
    public boolean detectAlgoSmallSells(int targetCount) {
        return this.consecutiveSmallSells >= targetCount;
    }

    /**
     * 【核心攻擊邏輯】偵測「連續隱蔽吸籌」的程式單
     */
    public boolean detectAlgoSmallBuys(int targetCount) {
        return this.consecutiveSmallBuys >= targetCount;
    }

    // --- 以下為原本的全日統計方法 ---
    public double getOutsideRatio() {
        long out = outsideVolume.sum();
        long in = insideVolume.sum();
        if (out + in == 0) return 50.0;
        return (double) out / (out + in) * 100.0;
    }

    public long getLargeNetBuy() {
        return largeBuyVolume.sum() - largeSellVolume.sum();
    }
    
    
    
    public String getSymbol() { return symbol; }
}