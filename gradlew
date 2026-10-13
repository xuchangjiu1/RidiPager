#!/bin/sh
# Gradle 启动脚本（Windows 下请用 gradlew.bat）
# 首次运行会自动下载 Gradle 8.2

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_BASE_NAME=$(basename "$0")
APP_HOME="$DIR"

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

exec "$JAVACMD" -Xmx64m -Xms64m \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
