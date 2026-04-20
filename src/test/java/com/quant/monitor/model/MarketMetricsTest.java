package com.quant.monitor.model;

import com.quant.monitor.model.MarketMetrics;
import com.quant.monitor.model.Tick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 針對 O(1) 狀態機的量化邏輯進行防呆與正確性驗證
 */
class MarketMetricsTest {

    private MarketMetrics metrics;

    // 在每一個 @Test 執行前，都會先執行這個方法，確保每次測試都是乾淨的狀態
    @BeforeEach
    void setUp() {
        metrics = new MarketMetrics("2330");
    }

    // 輔助方法：用來快速生成假數據 (Mock Data)
    private Tick createMockTick(int bsFlag) {
        return new Tick("2330", LocalTime.now(), 1000.0, 10, bsFlag);
    }

    @Test
    @DisplayName("測試：連續 8 筆內盤賣單應精準觸發警報")
    void testContinuousSells_TriggerAlert() {
        // 模擬連續 7 筆內盤賣單 (bsFlag = -1)
        for (int i = 0; i < 7; i++) {
            metrics.processTick(createMockTick(-1), 50);
            assertFalse(metrics.detectAlgoSmallSells(8), "未滿 8 筆前，絕對不應觸發誤報");
        }

        // 灌入第 8 筆
        metrics.processTick(createMockTick(-1), 50);
        assertTrue(metrics.detectAlgoSmallSells(8), "第 8 筆內盤必須精準觸發主力倒貨警報");
    }

    @Test
    @DisplayName("測試：遇到外盤買單應立刻切斷連續賣壓計數 (狀態歸零)")
    void testBuyOrder_ResetsSellCounter() {
        // 先灌入 5 筆內盤
        for (int i = 0; i < 5; i++) {
            metrics.processTick(createMockTick(-1), 50);
        }

        // 突然出現 1 筆外盤買單 (bsFlag = 1)
        metrics.processTick(createMockTick(1), 50);

        // 再補 3 筆內盤 (加起來總共 8 筆內盤，但中間斷掉了)
        for (int i = 0; i < 3; i++) {
            metrics.processTick(createMockTick(-1), 50);
        }

        assertFalse(metrics.detectAlgoSmallSells(8), "由於中間遭遇買單抵抗，連續狀態已被打破，不應觸發警報");
    }

    @Test
    @DisplayName("測試核心面試題：遇到中性單 (bsFlag=0) 應濾除雜訊，保持原計數狀態")
    void testNoiseFiltering_NeutralTickDoesNotReset() {
        // 先灌入 7 筆內盤
        for (int i = 0; i < 7; i++) {
            metrics.processTick(createMockTick(-1), 50);
        }

        // 💡 關鍵挑戰：插入一筆中性單/試撮單 (bsFlag = 0)
        metrics.processTick(createMockTick(0), 50);
        assertFalse(metrics.detectAlgoSmallSells(8), "中性單不該被當作賣壓觸發");

        // 再來一筆內盤，應該要接續前面的 7，變成 8 並觸發
        metrics.processTick(createMockTick(-1), 50);
        assertTrue(metrics.detectAlgoSmallSells(8), "中性雜訊單不應中斷計數，第 8 筆有效賣單進入時必須觸發警報");
    }
}