#!/usr/bin/env bash
# 二进制兼容性检查：参考桩 jar 里每个 public/protected 成员（字段/方法/构造）必须在候选中存在。
# 候选多出的成员不算问题（向后兼容的超集）。用法：
#   bash check_abi.sh [参考.jar] [候选classes目录]
# 默认参考 third_party/android-car-stub.jar，候选 api/build/classes。
set -euo pipefail

API_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$API_DIR/../../.." && pwd)"
REF_JAR="${1:-$REPO_DIR/third_party/android-car-stub.jar}"
CAND_DIR="${2:-$API_DIR/build/classes}"

native_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s\n' "$1"
    fi
}

if [ -n "${JAVA_HOME:-}" ]; then
    JDK_HOME="$JAVA_HOME"
    if command -v cygpath >/dev/null 2>&1 && [[ "$JAVA_HOME" == *\\* ]]; then
        JDK_HOME="$(cygpath -u "$JAVA_HOME")"
    fi
else
    JDK_HOME="/d/Tools/Android/jdk-17/jdk-17.0.21+9"
fi
JAVAP="$JDK_HOME/bin/javap"
JAR_TOOL="$JDK_HOME/bin/jar"
if [ ! -x "$JAVAP" ]; then
    echo "错误：找不到 javap（JAVA_HOME=$JDK_HOME）" >&2
    exit 1
fi
if [ ! -f "$REF_JAR" ]; then
    echo "错误：找不到参考 jar：$REF_JAR" >&2
    exit 1
fi
if [ ! -d "$CAND_DIR" ]; then
    echo "错误：找不到候选 classes 目录：$CAND_DIR（先运行 build.sh）" >&2
    exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ref"
( cd "$TMP/ref" && "$JAR_TOOL" --extract --file "$(native_path "$REF_JAR")" >/dev/null )

# 输出规范化的 API 行：H|<类头> / F|<字段名>|<描述符> / M|<方法名>|<描述符>（泛型擦除）。
# 只看 public/protected 成员（javap -protected），private 内部实现不参与兼容性判断。
api_of() {
    "$JAVAP" -s -protected -classpath "$(native_path "$1")" "$2" 2>/dev/null | awk '
        function stripg(s) { while (gsub(/<[^<>]*>/, "", s) > 0) {} return s }
        /^Compiled from/ { next }
        /^[^ \t]/ {
            h = stripg($0)
            sub(/[ \t]*\{[ \t]*$/, "", h)
            print "H|" h
            next
        }
        /^  [^ \t]/ { pending = $0; next }
        /^    descriptor: / {
            d = $0
            sub(/^    descriptor: /, "", d)
            if (pending != "") {
                line = stripg(pending)
                sub(/^[ \t]+/, "", line)
                if (index(line, "(") > 0) {
                    name = substr(line, 1, index(line, "(") - 1)
                    sub(/[ \t]+$/, "", name)
                    n = split(name, parts, /[ \t]+/)
                    print "M|" parts[n] "|" d
                } else {
                    sub(/;[ \t]*$/, "", line)
                    n = split(line, parts, /[ \t]+/)
                    print "F|" parts[n] "|" d
                }
                pending = ""
            }
            next
        }
    '
}

members() {
    printf '%s\n' "$1" | awk '/^[MF]\|/' | sort -u
}

echo "ABI 检查：参考 $(basename "$REF_JAR") -> 候选 $CAND_DIR"
status=0
total=0
class_ok=0
missing_members=0
extra_members=0

while IFS= read -r rel; do
    rel="${rel#./}"
    file="${rel%.class}"
    fqcn="${file//\//.}"
    total=$((total + 1))

    ref_api="$(api_of "$TMP/ref" "$fqcn" || true)"
    ref_hdr="$(printf '%s\n' "$ref_api" | sed -n 's/^H|//p' | head -1)"
    cand_api="$(api_of "$CAND_DIR" "$fqcn" || true)"

    if [ -z "$cand_api" ]; then
        case "$ref_hdr" in
            public*|protected*)
                echo "  [FAIL] $fqcn：候选缺类（参考为 $ref_hdr）"
                status=1
                ;;
            *)
                echo "  [skip] $fqcn：非 public 内部类，候选可缺省（参考为 $ref_hdr）"
                ;;
        esac
        continue
    fi

    cand_hdr="$(printf '%s\n' "$cand_api" | sed -n 's/^H|//p' | head -1)"
    if [ "$ref_hdr" != "$cand_hdr" ]; then
        echo "  [FAIL] $fqcn：类头不一致"
        echo "         参考：$ref_hdr"
        echo "         候选：$cand_hdr"
        status=1
        continue
    fi

    members "$ref_api" > "$TMP/ref.members"
    members "$cand_api" > "$TMP/cand.members"
    missing="$(comm -23 "$TMP/ref.members" "$TMP/cand.members" || true)"
    extra="$(comm -13 "$TMP/ref.members" "$TMP/cand.members" || true)"
    if [ -n "$missing" ]; then
        echo "  [FAIL] $fqcn：缺少 public/protected 成员"
        printf '%s\n' "$missing" | sed 's/^/           /'
        missing_members=$((missing_members + $(printf '%s\n' "$missing" | wc -l)))
        status=1
    else
        n_extra="$(printf '%s\n' "$extra" | grep -c . || true)"
        extra_members=$((extra_members + n_extra))
        class_ok=$((class_ok + 1))
        echo "  [OK]   $fqcn（超集新增成员 $n_extra）"
    fi
done < <(cd "$TMP/ref" && find . -name '*.class' | sort)

echo "----"
echo "参考类 $total 个，通过 $class_ok 个，缺失成员 $missing_members 个，超集新增成员 $extra_members 个"
if [ "$status" -eq 0 ]; then
    echo "ABI 结论：兼容"
else
    echo "ABI 结论：不兼容" >&2
fi
exit "$status"
