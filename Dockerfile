FROM amazoncorretto:17-alpine3.17
LABEL authors="hoywu"

ENV TZ=Asia/Shanghai
ENV DOCKER=true
ENV debug=0

COPY target/*jar-with-dependencies.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]