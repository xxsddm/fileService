# 使用Maven构建阶段
FROM maven:3.8.5-openjdk-17 AS build

# 设置工作目录
WORKDIR /app

# 复制父pom.xml
COPY pom.xml ./

# 复制file-service的pom.xml
COPY file-service/pom.xml ./file-service/

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY file-service/src ./file-service/src

# 构建应用
RUN mvn clean package -pl file-service -am -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 创建上传目录
RUN mkdir -p /data/uploads

# 复制构建的jar文件
COPY --from=build /app/file-service/target/file-service-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]