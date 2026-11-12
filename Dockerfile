FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
COPY --from=frontend-build /workspace/frontend/dist/ backend/src/main/resources/static/
RUN mvn -B -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS app
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=backend-build /workspace/backend/target/findmypacks-1.0.0.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
  CMD curl -fsS http://localhost:8080/api/web/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM maven:3.9.9-eclipse-temurin-21-alpine AS tests
RUN apk add --no-cache nodejs npm
WORKDIR /workspace
COPY backend/ backend/
COPY frontend/ frontend/
CMD ["sh", "-c", "npm --prefix frontend ci && npm --prefix frontend run test:run && mvn -B -f backend/pom.xml verify"]
