package com.quant.monitor.service;

import com.quant.monitor.model.Tick;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 滾動時間窗佇列 (Rolling Time Window)
 * 專門負責暫存最近 N 分鐘內的 Tick，並自動剔除過期資料，保護系統記憶體
 */
public class TimeWindowQueue {

    // 使用雙向並發佇列，確保高頻寫入與讀取時的絕對執行緒安全 (Thread-Safe)
    private final ConcurrentLinkedDeque<Tick> queue = new ConcurrentLinkedDeque<>();
    
    // 時間窗的大小 (例如：5 分鐘)
    private final Duration windowSize;

    public TimeWindowQueue(Duration windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * 將最新的 Tick 加入佇列的尾端，並同時觸發頭部的清理機制
     */
    public void addAndEvict(Tick newTick) {
        // 1. 將新資料加入尾巴
        queue.addLast(newTick);
        
        // 2. 檢查並剔除過期資料
        evictOldTicks(newTick.time());
    }

    /**
     * 核心防禦機制：剔除超過時間窗的舊資料
     * @param currentTime 最新一筆 Tick 的時間
     */
    private void evictOldTicks(LocalTime currentTime) {
        Tick head;
        // 無鎖設計 (Lock-Free)：不斷偷看 (peek) 排在最前面的那一筆
        while ((head = queue.peekFirst()) != null) {
            
            // 計算最舊的那筆資料，距離現在差了多久
            Duration age = Duration.between(head.time(), currentTime);
            
            // 如果年紀 (age) 大於我們設定的窗口大小 (windowSize)
            if (age.compareTo(windowSize) > 0) {
                // 把過期的舊資料從頭部拔除 (交給 Java 的 GC 去回收)
                queue.pollFirst(); 
            } else {
                // 因為資料是按照時間順序進來的，只要頭部沒過期，後面的就絕對沒過期，直接跳出迴圈
                break; 
            }
        }
    }

    /**
     * 提供給演算法層讀取目前窗口內的所有 Tick (唯讀視角)
     */
    public Iterable<Tick> getSnapshot() {
        return queue;
    }
    
    // 取得目前佇列內還有幾筆資料 (主要用於測試與監控)
    public int size() {
        return queue.size();
    }
}