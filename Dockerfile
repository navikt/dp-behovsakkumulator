FROM cgr.dev/chainguard/jre:openjdk-17

COPY build/libs/*.jar /app/

CMD ["-jar", "dp-behovsakkumulator-fat.jar"]