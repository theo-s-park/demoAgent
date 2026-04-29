FROM bellsoft/liberica-openjdk-alpine:25 AS builder
WORKDIR /app
COPY . .
# gradle.properties의 Windows 절대경로 제거 후 빌드
RUN sed -i '/org\.gradle\.java\.home/d' gradle.properties \
    && chmod +x gradlew \
    && ./gradlew build -x test --no-daemon

FROM bellsoft/liberica-openjre-alpine:25
# Python + 동적 도구 실행 환경
RUN apk add --no-cache python3 py3-pip \
    && pip3 install --no-cache-dir --break-system-packages \
       fastapi==0.115.0 uvicorn==0.30.6 httpx==0.27.0 pydantic==2.9.2 python-dotenv==1.0.1
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
# 정적 도구 파일 복사 (동적 생성 py는 런타임에 이 디렉토리에 생성됨)
COPY tool-server/ /app/tool-server/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
