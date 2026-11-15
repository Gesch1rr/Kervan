# syntax=docker/dockerfile:1

# Build stage: cache Maven dependencies separately from application sources.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
# Download dependencies first: this layer is reused as long as pom.xml does not change.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

# Copy sources after dependencies so source-only changes do not re-download Maven artifacts.
COPY src src
# Tests run in CI, not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package

# Pick the boot jar explicitly; target/*.jar can also match *.jar.original.
RUN find target -maxdepth 1 -name '*.jar' ! -name '*.original' -exec cp {} app.jar \;

# Extract Spring Boot layers for a cache-friendly runtime image.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /workspace/extracted

# Runtime stage.
FROM eclipse-temurin:25-jre-alpine

# Run as a non-root user.
RUN addgroup -S kervan && adduser -S kervan -G kervan
WORKDIR /app

# Rarely changing layers first: dependencies, loader, snapshot dependencies, then application.
# This keeps rebuilds fast when only application code changes.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER kervan

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
