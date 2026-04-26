FROM eclipse-temurin:21-jre

ENV KAFKA_HOME=/opt/kafka
ENV KAFKA_CONFIG_FILE=/etc/kafka/server.properties

COPY kafka-dist/ /opt/kafka/
COPY server.properties /etc/kafka/server.properties
COPY entrypoint.sh /usr/local/bin/kafka-drr-entrypoint.sh

RUN chmod +x /usr/local/bin/kafka-drr-entrypoint.sh \
    && mkdir -p /var/lib/kafka/data /etc/kafka

EXPOSE 9092 9093

VOLUME ["/var/lib/kafka/data"]

ENTRYPOINT ["/usr/local/bin/kafka-drr-entrypoint.sh"]
