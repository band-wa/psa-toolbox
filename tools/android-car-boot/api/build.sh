#!/usr/bin/env bash
# 构建 bootclasspath 版 android.car 客户端：javac(JDK17, --release 8) -> d8 -> jar(classes.dex)。
# 可重复执行；产物在 build/android.car.jar。用 Git Bash 运行：bash tools/android-car-boot/api/build.sh
set -euo pipefail

API_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$API_DIR/../../.." && pwd)"
SRC_DIR="$API_DIR/src"
# bootclasspath 版客户端与服务端共用 boot 协议源码（BootCarProtocol，事务码 >=0x1000）。
PROTO_DIR="$REPO_DIR/tools/android-car-boot/protocol/java"
BUILD_DIR="$API_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
JAR_ROOT="$BUILD_DIR/jar-root"
OUT_JAR="$BUILD_DIR/android.car.jar"

native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

# ---- 工具链定位（均可用环境变量覆盖） ----
ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/d/Tools/Android/sdk}}"
if [ ! -d "$ANDROID_SDK" ] && [ -f "$REPO_DIR/local.properties" ]; then
    ANDROID_SDK="$(sed -n 's/^sdk\.dir[[:space:]]*=[[:space:]]*//p' "$REPO_DIR/local.properties" \
        | tr -d '\r' | head -1)"
fi
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_SDK/platforms/android-34/android.jar}"
D8="${D8:-$ANDROID_SDK/build-tools/34.0.0/d8.bat}"
DEXDUMP="${DEXDUMP:-$ANDROID_SDK/build-tools/34.0.0/dexdump.exe}"

if [ -n "${JAVA_HOME:-}" ]; then
    JDK_HOME="$JAVA_HOME"
    if command -v cygpath >/dev/null 2>&1 && [[ "$JAVA_HOME" == *\\* ]]; then
        JDK_HOME="$(cygpath -u "$JAVA_HOME")"
    fi
else
    JDK_HOME="/d/Tools/Android/jdk-17/jdk-17.0.21+9"
fi
JAVAC="$JDK_HOME/bin/javac"
JAR_TOOL="$JDK_HOME/bin/jar"
if [ ! -x "$JAVAC" ] || [ ! -x "$JAR_TOOL" ]; then
    echo "错误：找不到 JDK17 的 javac/jar（JAVA_HOME=$JDK_HOME）" >&2
    exit 1
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "错误：找不到 android.jar（ANDROID_JAR=$ANDROID_JAR）" >&2
    exit 1
fi
if [ ! -f "$D8" ]; then
    echo "错误：找不到 d8（D8=$D8）" >&2
    exit 1
fi

echo "== 清理 $BUILD_DIR"
rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR" "$DEX_DIR" "$JAR_ROOT"

# ---- 1. javac：编译 6 个 android.car 类 + boot 协议类 ----
SOURCES=()
while IFS= read -r f; do
    SOURCES+=("$f")
done < <(find "$SRC_DIR" "$PROTO_DIR" -name '*.java' | sort)
echo "== javac ${#SOURCES[@]} 个源文件（--release 8）"
"$JAVAC" --release 8 -encoding UTF-8 \
    -classpath "$(native_path "$ANDROID_JAR")" \
    -d "$(native_path "$CLASSES_DIR")" \
    "${SOURCES[@]}"
CLASS_COUNT="$(find "$CLASSES_DIR" -name '*.class' | wc -l | tr -d ' ')"
echo "   class 文件数：$CLASS_COUNT"

# ---- 2. d8：classes -> classes.dex（min-api 24） ----
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

# ---- 3. jar：根目录放 classes.dex，作为 bootclasspath jar ----
cp "$DEX_DIR/classes.dex" "$JAR_ROOT/classes.dex"
( cd "$JAR_ROOT" && "$JAR_TOOL" --create --file "$(native_path "$OUT_JAR")" \
    --no-manifest --date=1980-01-01T00:00:02Z classes.dex )
JAR_SIZE="$(stat -c %s "$OUT_JAR" | tr -d ' ')"

# ---- 4. 报告 ----
echo "== 产物：$OUT_JAR（$JAR_SIZE 字节）"
echo "== jar 内容："
"$JAR_TOOL" --list --file "$(native_path "$OUT_JAR")" | sed 's/^/   /'
if [ -f "$DEXDUMP" ]; then
    echo "== dex 中的类（$("$DEXDUMP" "$(native_path "$DEX_DIR/classes.dex")" 2>/dev/null \
        | grep -c "Class descriptor")）："
    "$DEXDUMP" "$(native_path "$DEX_DIR/classes.dex")" 2>/dev/null \
        | sed -n "s/.*Class descriptor *: '\(.*\)'/\1/p" | sed 's/^/   /'
else
    echo "（未找到 dexdump，跳过 dex 类清单）"
fi

# ---- 5. ABI 兼容性检查（参考桩 jar 存在时） ----
if [ -f "$API_DIR/check_abi.sh" ] && [ -f "$REPO_DIR/third_party/android-car-stub.jar" ]; then
    echo "== ABI 检查（参考 third_party/android-car-stub.jar）"
    bash "$API_DIR/check_abi.sh" "$REPO_DIR/third_party/android-car-stub.jar" "$CLASSES_DIR"
fi
