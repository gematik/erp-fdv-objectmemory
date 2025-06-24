FROM eclipse-temurin:17-jdk
# Create a non-privileged user that the app will run under.
# See https://docs.docker.com/go/dockerfile-user-best-practices/


# Copy the executable from the "package" stage.
#COPY --from=package build/target/app.jar app.jar
#COPY ./target/ app.jar
COPY target/omem-server-jar-with-dependencies.jar/ app.jar
#COPY src/main/webapp webapp/
#COPY src/main/java/de/gematik/test/erezept/remotefdv/server/config/config.yaml app/config.yaml
#COPY src/main/java/de/gematik/test/erezept/remotefdv/server/config/secret.ppcs app/secret.ppcs


EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]


