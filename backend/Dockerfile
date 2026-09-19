# ---- Giai doan 1: bien dich ----
# Dung anh Maven co san JDK de khong phu thuoc may cai dat gi.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy rieng pom truoc de Docker cache duoc lop tai thu vien: chi khi pom doi
# moi phai tai lai, sua code Java thi dung cache.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Giai doan 2: chay ----
# Anh JRE goi nhe hon anh JDK rat nhieu.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Chay bang tai khoan thuong, khong phai root.
RUN addgroup -S printnest && adduser -S printnest -G printnest

COPY --from=build /build/target/printnest-backend-*.jar app.jar

# Thu muc luu file tam; gan volume vao day neu muon file song qua nhieu lan deploy.
RUN mkdir -p /app/storage && chown -R printnest:printnest /app
USER printnest

ENV APP_STORAGE_PATH=/app/storage
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
# PORT do nen tang (Render/Railway) bom vao; application.yml da doc bien nay.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
