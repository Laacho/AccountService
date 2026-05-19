FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/account-service-1.0.0-SNAPSHOT.jar account-service.jar
EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "account-service.jar"]