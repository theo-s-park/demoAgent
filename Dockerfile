FROM bellsoft/liberica-openjdk-alpine:25 AS builder
WORKDIR /app
COPY . .
# gradle.properties의 Windows 절대경로 제거 후 빌드
RUN sed -i '/org\.gradle\.java\.home/d' gradle.properties \
    && chmod +x gradlew \
    && ./gradlew build -x test --no-daemon

FROM bellsoft/liberica-openjre-alpine:25
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
