#!/bin/bash
# Trusted wrapper around bubblewrap. It is started INSIDE a systemd user scope that carries the cgroup limits
# (see judge.Sandbox), so everything started here is counted against those limits.
#
# usage: run-in-sandbox.sh <strict|probe> <source-dir> <command...>
#
#  strict : the profile a learner submission runs in. Only the JDK, the libc it needs, the trusted runtime classes and the
#           one source file are visible. There is no shell and no other program in the sandbox.
#  probe  : strict plus all of /usr (a shell, python3, coreutils). Used ONLY by the adversarial suite to attack the sandbox
#           with real tools (for example python3 ctypes system calls). Never used for a learner submission.
set -eu
BASE="$(cd "$(dirname "$0")" && pwd)"
PROFILE="$1"
SRC="$2"
shift 2

args=(
  --unshare-all --unshare-user   # user, ipc, pid, net, uts, cgroup namespaces: an empty network, its own process tree
  --die-with-parent --new-session
  --clearenv               # no environment variable of the driver reaches the sandbox
  --disable-userns         # no nested user namespaces
  --cap-drop ALL
  --hostname sandbox
  --ro-bind "$BASE/jdk17" /jdk
  --ro-bind "$BASE/build/rt" /judge/rt
  --ro-bind "$SRC" /src
  --size 33554432 --tmpfs /tmp
  --proc /proc --dev /dev
  --setenv PATH /jdk/bin --setenv HOME /tmp --setenv LANG C.UTF-8
  --chdir /tmp
)

if [ "$PROFILE" = "probe" ]; then
  args+=( --ro-bind /usr /usr --symlink usr/bin /bin --symlink usr/lib /lib --symlink usr/lib64 /lib64 --setenv PATH /jdk/bin:/usr/bin )
else
  args+=( --ro-bind /usr/lib/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu --ro-bind /usr/lib64 /usr/lib64
          --symlink usr/lib /lib --symlink usr/lib64 /lib64 )
fi

# nothing outside /tmp may be written: the root and /dev are tmpfs mounts and are writable unless remounted read-only
args+=( --remount-ro /dev --remount-ro / )

exec bwrap "${args[@]}" --seccomp 9 "$@" 9<"$BASE/seccomp.bpf"
