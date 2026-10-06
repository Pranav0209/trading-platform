#!/usr/bin/env bash
set -e

echo "Creating main topics..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic orders --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000

docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic trade-events --partitions 3 --replication-factor 1 \
  --config retention.ms=2592000000

docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic market-data --partitions 6 --replication-factor 1 \
  --config retention.ms=86400000

echo "Creating dead-letter topics (DLT)..."
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic orders.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000

docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic trade-events.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000

docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic market-data.DLT --partitions 1 --replication-factor 1 \
  --config retention.ms=2592000000

echo "Topics created successfully."
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
