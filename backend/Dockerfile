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
# Bon tuy chon nay deu de KHONG bi he dieu hanh giet trong container 2 GB.
#
# MaxRAMPercentage=60 (truoc la 75). Dung TIF xai nhieu bo nho NGOAI heap: ImageIO,
# chuyen mau ICC va PDFBox deu cap phat ngoai. Heap 75% cua may 2 GB la 1,5 GB, cong phan
# ngoai do la RSS vuot 2 GB - luc do he dieu hanh giet thang tien trinh, KHONG phai JVM
# nem OutOfMemoryError, nen khong co cho nao bat duoc. Da xay ra that tren Render:
# "Ran out of memory (used over 2GB)", may chu khoi dong lai va moi lan ghep dang giu
# trong bo nho mat sach - tho bam tai TIF thi nhan JOB_NOT_FOUND.
#
# Ba tuy chon con lai de TRA BO NHO LAI cho he dieu hanh luc ranh. Heap da no ra thi JVM
# giu nguyen phan da cap phat, nen do thi RAM tren Render nam phang o 80% suot dem du CPU
# bang 0. Lan xuat TIF sau chi can xin them vai tram MB la cham tran.
#   UseG1GC                        chon ro G1: tren may 1 CPU, JVM mac dinh chon SerialGC,
#                                  ma SerialGC khong co co che thu hoi dinh ky
#   G1PeriodicGCInterval=60000     ranh thi cu 60 giay chay mot chu ky don, va tra lai
#                                  phan heap khong dung
#   G1PeriodicGCSystemLoadThreshold=0  khong xet tai he thong, cu den gio la chay
#   Max/MinHeapFreeRatio=30/10     don xong thi co heap lai, chi chua toi da 30% cho trong
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 \
-XX:+UseG1GC \
-XX:G1PeriodicGCInterval=60000 \
-XX:G1PeriodicGCSystemLoadThreshold=0 \
-XX:MinHeapFreeRatio=10 \
-XX:MaxHeapFreeRatio=30"

EXPOSE 8080
# PORT do nen tang (Render/Railway) bom vao; application.yml da doc bien nay.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
