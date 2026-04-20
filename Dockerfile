# ==========================================
# Stage 1: Build 環境 (負責編譯原始碼)
# ==========================================
# 使用包含 Maven 與 JDK 21 的官方映像檔作為建置環境
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# 設定工作目錄
WORKDIR /build

# 【架構師細節 1：利用 Docker Layer Cache】
# 先只複製 pom.xml，並下載所有相依套件。
# 只要 pom.xml 沒變，未來修改程式碼重新 build 時，這一步會被快取，省下大量下載時間！
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 複製真正的原始碼
COPY src ./src

# 執行編譯並打包成 JAR 檔 (跳過測試，因為我們在 CI 階段會測)
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Runtime 環境 (負責執行應用程式)
# ==========================================
# 【架構師細節 2：映像檔瘦身】
# 這裡只使用 JRE (Java Runtime Environment) 與極度輕量級的 Alpine Linux
# 不需要 Maven，也不需要 JDK，能將映像檔從 800MB 瘦身到 150MB 左右，並降低資安風險
FROM eclipse-temurin:21-jre-alpine

# 建立一個非 root 的使用者來執行程式 (資安最佳實踐)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

# 從 Stage 1 (builder) 把編譯好的 JAR 檔複製過來
COPY --from=builder /build/target/*.jar app.jar

# 宣告對外的 Port (8080 給 Prometheus 抓 Metrics 用的)
EXPOSE 8080

# 啟動應用程式
ENTRYPOINT ["java", "-jar", "app.jar"]