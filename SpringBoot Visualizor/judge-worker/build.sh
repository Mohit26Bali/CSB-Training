#!/bin/bash
# Builds the judge-worker proof of concept inside Ubuntu WSL. Run it from WSL:  bash /mnt/d/.../judge-worker/build.sh
# Everything is placed under ~/springlab-judge (JDK 17 is expected in ~/springlab-judge/jdk17).
set -eu
SRC="$(cd "$(dirname "$0")" && pwd)"
BASE="$HOME/springlab-judge"
JDK="$BASE/jdk17"
[ -x "$JDK/bin/javac" ] || { echo "missing $JDK/bin/javac"; exit 1; }

mkdir -p "$BASE/build/rt" "$BASE/build/driver" "$BASE/build/suite" "$BASE/jobs"
cp "$SRC/sandbox/run-in-sandbox.sh" "$BASE/run-in-sandbox.sh"
chmod 755 "$BASE/run-in-sandbox.sh"
python3 "$SRC/sandbox/gen_seccomp.py" "$BASE/seccomp.bpf"

"$JDK/bin/javac" --release 17 -proc:none -d "$BASE/build/rt" "$SRC"/rt/*.java
"$JDK/bin/javac" --release 17 -proc:none -d "$BASE/build/driver" "$SRC"/driver/judge/*.java
if [ -d "$SRC/suite" ]; then
  "$JDK/bin/javac" --release 17 -proc:none -cp "$BASE/build/driver" -d "$BASE/build/suite" "$SRC"/suite/judge/*.java
fi
echo "built into $BASE/build"
