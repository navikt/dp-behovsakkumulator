FROM cgr.dev/chainguard/jre:latest

COPY build/libs/*.jar /app/

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

CMD ["-jar", "dp-behovsakkumulator-fat.jar"]
