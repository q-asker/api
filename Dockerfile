FROM eclipse-temurin:21-jre-noble

RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends \
      libreoffice \
      libreoffice-java-common \
    && rm -rf /var/lib/apt/lists/*
