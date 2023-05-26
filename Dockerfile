FROM amazoncorretto:17-alpine3.17
LABEL authors="hoywu"

ADD target/*.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]