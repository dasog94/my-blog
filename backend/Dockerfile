FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon installDist

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=app:app /workspace/build/install/jimmy-blog-api/ ./
USER app
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
CMD ["/app/bin/jimmy-blog-api"]
