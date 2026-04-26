#!/usr/bin/env bash
set -euo pipefail

PATCHED_KAFKA_ROOT="${PATCHED_KAFKA_ROOT:-/tmp/kafka-4.1.0-drr}"
IMAGE_NAME="${IMAGE_NAME:-kafka-drr}"
IMAGE_TAG="${IMAGE_TAG:-4.1.0}"
SCALA_VERSION="${SCALA_VERSION:-2.13}"
JAVA_HOME_DEFAULT="/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"

if [[ ! -d "$PATCHED_KAFKA_ROOT" ]]; then
  echo "Patched Kafka root not found: $PATCHED_KAFKA_ROOT"
  exit 1
fi

if [[ ! -x "$PATCHED_KAFKA_ROOT/gradlew" ]]; then
  echo "gradlew not found or not executable: $PATCHED_KAFKA_ROOT/gradlew"
  exit 1
fi

if [[ ! -d "$JAVA_HOME" ]]; then
  echo "JDK 21 not found at JAVA_HOME=$JAVA_HOME"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$REPO_ROOT/docker/drr"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gradle-home}"
TMP_CONTEXT="$(mktemp -d /tmp/kafka-drr-docker-context.XXXXXX)"

cleanup() {
  rm -rf "$TMP_CONTEXT"
}
trap cleanup EXIT

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME

echo "[1/4] Building patched Kafka release tarball from $PATCHED_KAFKA_ROOT"
(
  cd "$PATCHED_KAFKA_ROOT"
  ./gradlew releaseTarGz -PscalaVersion="$SCALA_VERSION" --no-daemon
)

TARBALL="$(find "$PATCHED_KAFKA_ROOT" -path "*/build/distributions/kafka_*.tgz" | head -n 1)"
if [[ -z "$TARBALL" ]]; then
  echo "Could not find built Kafka tarball under $PATCHED_KAFKA_ROOT"
  exit 1
fi

echo "[2/4] Preparing docker build context from $(basename "$TARBALL")"
mkdir -p "$TMP_CONTEXT/kafka-dist"
tar xfz "$TARBALL" -C "$TMP_CONTEXT/kafka-dist" --strip-components 1
cp "$DOCKER_DIR/Dockerfile" "$TMP_CONTEXT/Dockerfile"
cp "$DOCKER_DIR/server.properties" "$TMP_CONTEXT/server.properties"
cp "$DOCKER_DIR/entrypoint.sh" "$TMP_CONTEXT/entrypoint.sh"

echo "[3/4] Building docker image $IMAGE_NAME:$IMAGE_TAG"
docker build -t "$IMAGE_NAME:$IMAGE_TAG" "$TMP_CONTEXT"

echo "[4/4] Done"
echo "Image ready: $IMAGE_NAME:$IMAGE_TAG"
