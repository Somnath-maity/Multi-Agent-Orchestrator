#!/bin/sh
# Maven Wrapper script for Unix/macOS/Linux
# Downloads Maven if not present, then runs it

set -e

MAVEN_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

DEFAULT_MVN_VERSION="3.9.9"

# Read distributionUrl
DOWNLOAD_URL=""
if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
    DOWNLOAD_URL=$(grep '^distributionUrl=' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
fi

if [ -z "$DOWNLOAD_URL" ]; then
    DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${DEFAULT_MVN_VERSION}/apache-maven-${DEFAULT_MVN_VERSION}-bin.zip"
fi

MAVEN_HOME_DIR="$HOME/.m2/wrapper/dists/apache-maven-${DEFAULT_MVN_VERSION}"

# Download if not present
if [ ! -f "$MAVEN_HOME_DIR/bin/mvn" ]; then
    echo "Downloading Maven from $DOWNLOAD_URL..."
    mkdir -p "$MAVEN_HOME_DIR"
    MAVEN_ZIP="$(mktemp /tmp/maven-XXXXXX.zip)"

    if command -v curl > /dev/null 2>&1; then
        curl -fsSL -o "$MAVEN_ZIP" "$DOWNLOAD_URL"
    elif command -v wget > /dev/null 2>&1; then
        wget -q -O "$MAVEN_ZIP" "$DOWNLOAD_URL"
    else
        echo "ERROR: Neither curl nor wget found. Install one to use Maven Wrapper."
        exit 1
    fi

    # Extract
    DIST_DIR="$HOME/.m2/wrapper/dists"
    mkdir -p "$DIST_DIR"
    unzip -qo "$MAVEN_ZIP" -d "$DIST_DIR"
    rm -f "$MAVEN_ZIP"

    if [ ! -f "$MAVEN_HOME_DIR/bin/mvn" ]; then
        echo "ERROR: Maven extraction failed."
        exit 1
    fi

    chmod +x "$MAVEN_HOME_DIR/bin/mvn"
    echo "Maven ${DEFAULT_MVN_VERSION} installed to $MAVEN_HOME_DIR"
fi

exec "$MAVEN_HOME_DIR/bin/mvn" "$@"
