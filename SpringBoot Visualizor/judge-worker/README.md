# judge-worker (Phase F2a proof of concept)

A standalone, dependency-free Java program that compiles and tests ONE learner source file inside a Linux sandbox, and the adversarial
suite that attacks that sandbox. It is **not** part of the SpringLab backend, is not wired into any API, and does not enable learner code
execution anywhere. It exists to answer one question: can WSL2 + bubblewrap enforce the limits an educational coding platform needs?

## Layout

| Path | What |
|---|---|
| `rt/` | Runs INSIDE the sandbox: `Launcher` (compiles the learner file with annotation processing off, then answers "run this scenario" requests), plus the provided `Sleeper` and `TransientException`; the scripted-exercise adapters (`*Adapter.java`, one per Phase F5 problem) and `ReflectiveAdapter`, one generic adapter for the everyday Phase F6 exercises (`new` builds the learner's object, `call method args` calls a public method the class declares; the contract of each exercise is fixed in `Adapters.java`) |
| `driver/judge/` | Trusted driver: `Judge` (verdicts), `Sandbox` (systemd scope + bubblewrap process, bounded output), `Scenarios` (the hidden tests and the reference model), `Limits`, `Main` (CLI) |
| `sandbox/` | `run-in-sandbox.sh` (bubblewrap arguments) and `gen_seccomp.py` (the seccomp-bpf deny-list) |
| `suite/judge/AdversarialSuite.java` | 39 bounded attacks and checks |
| `build.sh` | Builds everything into `~/springlab-judge` inside Ubuntu WSL |

## Requirements (Ubuntu WSL, no sudo, no Docker)

* `bubblewrap` (0.11.1 here), `systemd` with a user manager (`systemd-run --user --scope` must work), cgroup v2, `python3`.
* A Linux JDK 17 at `~/springlab-judge/jdk17` (Temurin 17.0.21+9 here, SHA-256 verified: `8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711`).

## Use

```bash
bash judge-worker/build.sh                                            # inside WSL
~/springlab-judge/jdk17/bin/java -cp ~/springlab-judge/build/driver judge.Main run|submit [--debug] <file | ->
~/springlab-judge/jdk17/bin/java -cp ~/springlab-judge/build/driver:~/springlab-judge/build/suite judge.AdversarialSuite
```

From Windows, source can be passed on stdin so no directory is shared:
`wsl.exe -d Ubuntu -- /home/<user>/springlab-judge/jdk17/bin/java -cp /home/<user>/springlab-judge/build/driver judge.Main submit - < Solution.java`

## Isolation layers

1. **cgroup v2 scope** (`systemd-run --user --scope`): `MemoryMax=384M`, `MemorySwapMax=0`, `TasksMax=64`, `CPUQuota=100%`. The kernel enforces these; killing the scope kills every process in it.
2. **bubblewrap**: new user, pid, net, ipc, uts and cgroup namespaces; `--disable-userns`; all capabilities dropped; `--clearenv`; read-only minimal root (JDK, libc, the trusted runtime classes, the one source file); a 32 MiB `/tmp` tmpfs; root and `/dev` remounted read-only; no `/mnt`, `/home` or `/etc`.
3. **seccomp-bpf deny-list**: ptrace, mount and the new mount API, namespace calls, setns, chroot, bpf, keys, perf, clock and host changes, reboot, INET/INET6/NETLINK/PACKET sockets; `clone` with namespace flags; `clone3` answered ENOSYS.
4. **JVM flags**: `-Xmx128m`, `-XX:MaxDirectMemorySize=16m`, `-XX:+ExitOnOutOfMemoryError`, small stacks.
5. **Driver**: output cap (64 KiB across both streams), line-length cap, per-test and total wall-clock limits, one scenario at a time so a hang is attributed to one test, source size and encoding checks before any sandbox starts.

## Hidden tests

The hidden tests (inputs AND expected observations) never enter the sandbox. The sandbox is told only a scenario's input and replies with
what it observed; the trusted driver compares that with a reference model. See the F2a report for the remaining risks.

## F2b additions (used by the backend adapter)

* `judge check`: verifies that the worker files, bubblewrap and systemd user scopes are present. Starts no learner code.
* `judge submit|run --framed --job <16 hex> -`: reads `LEN n` plus n bytes of source from stdin and KEEPS stdin open. If stdin closes (the caller was killed or restarted) or the process gets SIGHUP/SIGTERM, the sandbox scope is killed and the job directory removed. The sandbox scope is named `sl-judge-<job>` so it can be cancelled.
* `judge cancel <16 hex>`: kills the scope of a job (every process in it).
* Every worker start removes job directories older than five minutes (a driver killed with SIGKILL cannot clean up after itself).
* The backend passes `-Djudge.base=<base dir>` so the worker always uses the directory it was started from.
