# Stage 1: Build file JAR dùng Maven và Java 17 từ Temurin
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build ứng dụng
RUN mvn clean package -DskipTests

# Stage 2: Chỉ chạy ứng dụng với JRE nhẹ (chỉ khoảng 200MB thay vì 500MB)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Mở cổng 8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]