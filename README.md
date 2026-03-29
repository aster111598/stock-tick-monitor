# Real-time Stock Tick Monitor (Java 21 / Spring Boot 4)

這是一個針對台股「逐筆成交」數據設計的高頻監控引擎。旨在解決盤中大戶利用「演算法拆單」隱蔽倒貨的偵測痛點。

## 🚀 技術核心與亮點 (Technical Highlights)

本專案拒絕使用傳統的重量級鎖，專為極致效能與現代化 Java 特性打造：

* **Java 21 + Spring Boot 4**: 全面採用 `Record` 定義不可變資料模型 (Immutable Data)，並準備好對接虛擬執行緒 (Virtual Threads)。
* **無鎖併發架構 (Lock-Free Concurrency)**: 
    * 使用 `LongAdder` 進行高頻累加，避免 `AtomicLong` 在高競爭下的 CPU 快取行爭用。
    * 使用 `ConcurrentLinkedDeque` 實作非阻塞式緩存。
* **讀寫分離巡邏機制**: 透過 `@Scheduled` 將「高頻寫入」與「重度運算」解耦，確保開盤爆量時 WebSocket 執行緒不阻塞。

## 🧠 籌碼辨識演算法 (Core Algorithms)

本系統不僅監控大單，更專注於抓取「不受震盪影響」的隱蔽規律：

1.  **狀態機連續性偵測 (State-Machine Continuity)**:
    * $O(1)$ 時間複雜度偵測連續 N 筆同向小單。
    * 自動偵測大單介入或力道轉折，即時歸零狀態計數。
2.  **滾動時間窗斜率分析 (Sliding Window Momentum)**:
    * 維護最近 5 分鐘的 `TimeWindowQueue`。
    * **時間分桶演算法**: 將 5 分鐘切分為前後場，計算程式單賣壓的「放大斜率」，有效過濾散戶零星買單雜訊。

## ⚙️ 快速啟動

1.  **環境需求**: JDK 21+, Maven 3.9+
2.  **設定 API**: 於 `WebSocketConfig.java` 中更換真實券商 WSS URL。
3.  **執行**: 
    ```bash
    mvn spring-boot:run
    ```

## 📈 未來擴充 (Roadmap)
- [ ] 實作斷線自動重連機制 (Auto-Reconnection)。
- [ ] 串接 LINE Notify / Telegram 實時警示。
- [ ] 導入虛擬執行緒優化百檔標的同步評估效能。