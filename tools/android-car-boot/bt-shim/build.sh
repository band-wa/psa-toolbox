#!/usr/bin/env bash
# 构建模拟蓝牙栈替身：javac --release 8 -> d8 -> jar(classes.dex)；产物由 avd/run.sh /
# run.ps1 在 boot 后推送并以 app_process 启动，可重复执行。
set -euo pipefail

SHIM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SHIM_DIR/../../.." && pwd)"
SRC_DIR="$SHIM_DIR/src"
BUILD_DIR="$SHIM_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
JAR_ROOT="$BUILD_DIR/jar-root"
OUT_JAR="$BUILD_DIR/bt-shim.jar"

HOST_OS="$(uname -s 2>/dev/null || echo Unknown)"
case "$HOST_OS" in
    MINGW*|MSYS*|CYGWIN*) HOST_OS=windows ;;
    Darwin) HOST_OS=darwin ;;
    *) HOST_OS=unix ;;
esac

native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

file_size() {
    stat -c %s "$1" 2>/dev/null || stat -f %z "$1"
}

first_existing() {
    for candidate in "$@"; do
        if [ -f "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

ANDROID_SDK="${ANDROID_SDK:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
if [ -z "$ANDROID_SDK" ] || [ ! -d "$ANDROID_SDK" ]; then
    if [ -f "$REPO_DIR/local.properties" ]; then
        ANDROID_SDK="$(sed -n 's/^sdk\.dir[[:space:]]*=[[:space:]]*//p' "$REPO_DIR/local.properties" \
            | tr -d '\r' | head -1)"
    fi
fi
if [ -z "$ANDROID_SDK" ] || [ ! -d "$ANDROID_SDK" ]; then
    if [ "$HOST_OS" = windows ] && [ -d /d/Tools/Android/sdk ]; then
        ANDROID_SDK=/d/Tools/Android/sdk
    fi
fi
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_SDK/platforms/android-34/android.jar}"

BT_DIR="${ANDROID_BUILD_TOOLS_DIR:-$ANDROID_SDK/build-tools/34.0.0}"
if [ "$HOST_OS" = windows ]; then
    D8="${D8:-$(first_existing "$BT_DIR/d8.bat" "$BT_DIR/d8" || true)}"
else
    D8="${D8:-$(first_existing "$BT_DIR/d8" "$BT_DIR/d8.bat" || true)}"
fi

if [ -n "${JAVA_HOME:-}" ]; then
    JDK_HOME="$JAVA_HOME"
    if command -v cygpath >/dev/null 2>&1 && [[ "$JAVA_HOME" == *\\* ]]; then
        JDK_HOME="$(cygpath -u "$JAVA_HOME")"
    fi
elif [ "$HOST_OS" = windows ] && [ -x "/d/Tools/Android/jdk-17/jdk-17.0.21+9/bin/javac" ]; then
    JDK_HOME="/d/Tools/Android/jdk-17/jdk-17.0.21+9"
elif command -v javac >/dev/null 2>&1; then
    JDK_HOME="$(cd "$(dirname "$(command -v javac)")/.." && pwd)"
else
    JDK_HOME="/d/Tools/Android/jdk-17/jdk-17.0.21+9"
fi
JAVAC="$JDK_HOME/bin/javac"
JAR_TOOL="$JDK_HOME/bin/jar"
if [ ! -x "$JAVAC" ] || [ ! -x "$JAR_TOOL" ]; then
    echo "错误：找不到 JDK17 的 javac/jar（JAVA_HOME=${JDK_HOME}，宿主=${HOST_OS}）" >&2
    exit 1
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "错误：找不到 android.jar（ANDROID_JAR=${ANDROID_JAR}）" >&2
    exit 1
fi
if [ ! -f "$D8" ]; then
    echo "错误：找不到 d8（D8=${D8:-<未找到>}，宿主=${HOST_OS}）" >&2
    exit 1
fi

echo "== 清理 $BUILD_DIR"
rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR" "$DEX_DIR" "$JAR_ROOT"

SOURCES=()
while IFS= read -r f; do
    SOURCES+=("$f")
done < <(find "$SRC_DIR" -name '*.java' | sort)
echo "== javac ${#SOURCES[@]} 个源文件（--release 8）"
"$JAVAC" --release 8 -encoding UTF-8 \
    -classpath "$(native_path "$ANDROID_JAR")" \
    -d "$(native_path "$CLASSES_DIR")" \
    "${SOURCES[@]}"

if ! command -v java >/dev/null 2>&1; then
    export PATH="$JDK_HOME/bin:$PATH"
fi
CLASS_FILES=()
while IFS= read -r f; do
    CLASS_FILES+=("$f")
done < <(cd "$CLASSES_DIR" && find . -name '*.class' | sed 's|^\./||' | sort)
echo "== d8 ${#CLASS_FILES[@]} 个 class -> classes.dex"
( cd "$CLASSES_DIR" && \
  JAVA_HOME="$(native_path "$JDK_HOME")" PATH="$JDK_HOME/bin:$PATH" \
  "$D8" --min-api 24 --lib "$(native_path "$ANDROID_JAR")" \
      --output "$(native_path "$DEX_DIR")" "${CLASS_FILES[@]}" )
if [ ! -f "$DEX_DIR/classes.dex" ]; then
    echo "错误：d8 未产出 classes.dex" >&2
    exit 1
fi

cp "$DEX_DIR/classes.dex" "$JAR_ROOT/classes.dex"
( cd "$JAR_ROOT" && "$JAR_TOOL" --create --file "$(native_path "$OUT_JAR")" \
    --no-manifest --date=1980-01-01T00:00:02Z classes.dex )
echo "== 产物：${OUT_JAR}（$(file_size "$OUT_JAR" | tr -d ' ') 字节）"
echo "== 启动方式（开发者模式 userdebug 设备，需 root）："
echo "   adb push $OUT_JAR /data/local/tmp/bt-shim.jar"
echo "   adb shell 'CLASSPATH=/data/local/tmp/bt-shim.jar app_process /system/bin com.bandwa.vcarlib.BtShim &'"
