#!/usr/bin/env sh
APP_BASE_NAME=`basename "$0"`
APP_HOME=`cd "${0%/*}" >/dev/null; pwd`
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
