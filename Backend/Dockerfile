# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle* .
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Tesseract OCR (tess4j 네이티브 의존성)
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
