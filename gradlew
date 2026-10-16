#!/bin/sh
#
# Gradle start up script (generated equivalent). If gradle-wrapper.jar is
# missing, Android Studio will offer to repair it, or run:
#   gradle wrapper --gradle-version 8.7
#

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve symlinks
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    case $link in
        /*) PRG="$link" ;;
        *) PRG=$(dirname "$PRG")"/$link" ;;
    esac
done
APP_HOME=$(cd "$(dirname "$PRG")" >/dev/null && pwd -P)

# Default JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use JAVA_HOME if set, else java on PATH
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "gradle-wrapper.jar not found. Open this project in Android Studio" >&2
    echo "(it will repair the wrapper) or run: gradle wrapper --gradle-version 8.7" >&2
    exit 1
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname="$APP_BASE_NAME" \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
