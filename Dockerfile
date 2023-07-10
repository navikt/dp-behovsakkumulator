FROM cgr.dev/chainguard/jre:latest

COPY build/libs/*.jar /app/

CMD ["-jar", "dp-behovsakkumulator-fat.jar"]
