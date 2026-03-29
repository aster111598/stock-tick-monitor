package com.quant.monitor.service;

import com.quant.monitor.model.Tick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalTime;

@SpringBootTest
class MarketDataServiceTest {

    @Autowired
    private MarketDataService marketDataService;

    @Test
    @DisplayName("模擬連續內盤小單 - 驗證狀態機警示")
    void testConsecutiveSmallSells() {
        String symbol = "2330";
        for (int i = 1; i <= 9; i++) {
            // 依照你的 Tick 定義：symbol, time, price, volume, bsFlag(-1 為內盤)
            Tick smallSell = new Tick(symbol, LocalTime.now(), 500.0, 2, -1);
            marketDataService.onTickReceived(smallSell);
        }
    }

    @Test
    @DisplayName("模擬賣壓動能放大 - 驗證 5 分鐘時間窗斜率")
    void testAmplifyingSellMomentum() {
        String symbol = "2454";
        LocalTime now = LocalTime.now();

        // 1. 前半場：5 筆，每筆 2 張
        for (int i = 0; i < 5; i++) {
            Tick oldTick = new Tick(symbol, now.minusSeconds(250), 1000.0, 2, -1);
            marketDataService.onTickReceived(oldTick);
        }

        // 2. 後半場：5 筆，每筆 6 張 (賣壓放大)
        for (int i = 0; i < 5; i++) {
            Tick newTick = new Tick(symbol, now, 995.0, 6, -1);
            marketDataService.onTickReceived(newTick);
        }
    }
}