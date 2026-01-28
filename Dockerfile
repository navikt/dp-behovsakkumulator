FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre@sha256:1139b90dd475b8a7f85150bea72f8cf17d35edf02c3ed8c1787f8d920b66d0c7

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"

COPY build/install/*/lib /app/lib

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.dagpenger.behovsakkumulator.AppKt"]
