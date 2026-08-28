FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY src/ src/

RUN ./gradlew --no-daemon clean installDist

FROM eclipse-temurin:25-jre

WORKDIR /opt/app

COPY --from=build /workspace/build/install/pnl-engine/ ./
COPY opening_positions.csv trades.csv funding.csv prices.csv ./

USER 10001:10001

ENTRYPOINT ["/opt/app/bin/pnl-engine"]
