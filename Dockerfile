FROM ubuntu:24.04
WORKDIR /app
COPY docker/runtime/java /opt/java
COPY docker/runtime/app.jar /app/app.jar
ENV JAVA_HOME=/opt/java
ENV PATH="/opt/java/bin:${PATH}"
ENV LANG=C.UTF-8
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
