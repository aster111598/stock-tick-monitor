package com.quant.monitor.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quant.monitor.model.Tick;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 逐筆交易資料解析器
 * 負責將券商傳來的 JSON 字串轉換為 Tick 實體物件
 */
@Component
public class TickParser {

    private final ObjectMapper objectMapper;

    public TickParser() {
        this.objectMapper = new ObjectMapper();
        
        // 【關鍵防禦機制：停損設定】
        // 強制忽略 JSON 中未知或多餘的欄位。券商 API 經常會無預警新增欄位，
        // 若不加上這行，遇到未知欄位時系統會直接拋出 Exception 崩潰。
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 註冊時間模組，讓 Jackson 能夠看懂 Tick 裡面的 LocalTime 型態
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 解析傳入的 JSON 字串
     * 回傳 Optional<Tick> 以避免 NullPointerException
     */
    public Optional<Tick> parse(String jsonMessage) {
        try {
            Tick tick = objectMapper.readValue(jsonMessage, Tick.class);
            return Optional.ofNullable(tick);
        } catch (JsonProcessingException e) {
            // 實戰中遇到壞資料不該讓系統停機，而是記錄下來並跳過
            // 未來這裡會換成企業級的 Log 框架 (如 SLF4J)
            System.err.println("⚠️ 偵測到異常格式或掉包資料，略過解析: " + e.getMessage());
            return Optional.empty();
        }
    }
}