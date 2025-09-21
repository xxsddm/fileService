// 使用JDK 21进行构建
FROM maven:3.8.5-openjdk-21 AS build

# 设置工作目录
WORKDIR /app

# pom.xml
COPY pom.xml ./

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn clean package -am -DskipTests

# 运行阶段 - 使用完整JDK 21以支持Arthas
FROM eclipse-temurin:21-jdk-alpine

# 创建上传目录
RUN mkdir -p /data/uploads

# 创建日志目录
RUN mkdir -p /data/logs

# 声明卷，用于持久化日志
VOLUME /data/logs

# 复制构建的jar文件
COPY --from=build /app/target/file-service-1.0.0.jar app.jar

# 设置环境变量，指定日志路径
ENV LOG_PATH=/data/logs

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]