# Sử dụng JDK 17 và Maven để build (Multi-stage build)
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build file jar và bỏ qua test để chạy nhanh
RUN mvn clean package -DskipTests

# Stage 2: Chỉ chạy ứng dụng với JRE nhẹ nhàng
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Mở cổng 8081 như cấu hình của bạn
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]