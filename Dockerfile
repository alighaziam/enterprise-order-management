FROM ubuntu:24.04

RUN useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --chown=appuser:appuser docker/runtime/java /opt/java
COPY --chown=appuser:appuser docker/runtime/app.jar /app/app.jar

ENV JAVA_HOME=/opt/java
ENV PATH="/opt/java/bin:${PATH}"
ENV LANG=C.UTF-8

USER appuser

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n" >&3 && IFS= read -r status <&3 && [[ "$status" == *" 200 "* ]]' || exit 1

STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
