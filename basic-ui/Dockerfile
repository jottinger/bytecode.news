FROM eclipse-temurin:25-jre
WORKDIR /app
COPY basic-ui/target/basic-ui-1.0.jar app.jar
ENV BACKEND_SCHEME=http
ENV BACKEND_HOST=backend:8080
EXPOSE 3003
ENTRYPOINT ["java", "-jar", "app.jar"]
