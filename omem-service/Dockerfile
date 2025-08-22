FROM eclipse-temurin:17-jdk

ARG COMMIT_HASH
ARG VERSION
ARG BUILD_DATE

ARG USER=omem
ARG GROUP=omem
ARG USER_HOME=/home/$USER
ARG UID=12345
ARG GID=23456

ENV VERSION=$VERSION
ENV COMMIT_HASH=$COMMIT_HASH
ENV BUILD_DATE=$BUILD_DATE

LABEL de.gematik.vendor="gematik GmbH"
LABEL maintainer="a.ibrokhimov@gematik.de"
LABEL de.gematik.app="E-Rezept Object Memory PoC"
LABEL de.gematik.git-repo-name="https://gitlab.prod.ccs.gematik.solutions/git/erezept/e-rezept-app-poc-objektspeicher"
LABEL de.gematik.commit-sha=$COMMIT_HASH
LABEL de.gematik.version=$VERSION

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y curl \
    && groupadd -g ${GID} ${GROUP} \
    && useradd -m -d ${USER_HOME} -g ${GROUP} -u ${UID} ${USER}


USER omem



COPY target/omem-server-jar-with-dependencies.jar/ app.jar


HEALTHCHECK CMD curl --fail http://localhost:8090/actuator/info || exit 1

EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]


