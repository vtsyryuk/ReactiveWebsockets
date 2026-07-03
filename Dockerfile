FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./gradlew clean installDist --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/build/install/ReactiveWebsockets/ ./

ENV PORT=8080
EXPOSE 8080

CMD ["bin/ReactiveWebsockets"]
