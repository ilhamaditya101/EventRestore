#!/bin/sh
set -e
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  mkdir -p "$DIR/gradle/wrapper"
  curl -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar" -o "$JAR"
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
