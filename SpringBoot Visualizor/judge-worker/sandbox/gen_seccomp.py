#!/usr/bin/env python3
"""Writes the seccomp-bpf program used by bubblewrap (--seccomp FD) for the judge sandbox. x86_64 only.

It is a DENY-LIST on top of the other layers (namespaces, no capabilities, cgroup limits), not the primary defence:
every blocked call returns EPERM (or ENOSYS for clone3, so glibc falls back to clone, which is inspected below).
Blocked: debugging other processes, mounting and namespace changes, kernel modules/keys/bpf/perf, clock and host changes, the
new mount API, and creating INET/INET6/NETLINK/PACKET sockets (the network namespace is already empty; this is defence in depth).
Usage: gen_seccomp.py <output file>
"""
import struct
import sys

LD_ABS_W = 0x20      # BPF_LD | BPF_W | BPF_ABS
JEQ = 0x15           # BPF_JMP | BPF_JEQ | BPF_K
JGE = 0x35           # BPF_JMP | BPF_JGE | BPF_K
JSET = 0x45          # BPF_JMP | BPF_JSET | BPF_K
RET = 0x06           # BPF_RET | BPF_K

AUDIT_ARCH_X86_64 = 0xC000003E
RET_ALLOW = 0x7FFF0000
RET_KILL_PROCESS = 0x80000000
EPERM, ENOSYS = 1, 38


def errno(n):
    return 0x00050000 | n


OFF_NR, OFF_ARCH, OFF_ARG0 = 0, 4, 16

DENY = {
    'ptrace': 101, 'process_vm_readv': 310, 'process_vm_writev': 311, 'mount': 165, 'umount2': 166, 'pivot_root': 155,
    'chroot': 161, 'unshare': 272, 'setns': 308, 'kexec_load': 246, 'kexec_file_load': 320, 'reboot': 169, 'swapon': 167,
    'swapoff': 168, 'init_module': 175, 'finit_module': 313, 'delete_module': 176, 'bpf': 321, 'perf_event_open': 298,
    'keyctl': 250, 'add_key': 248, 'request_key': 249, 'open_by_handle_at': 304, 'name_to_handle_at': 303, 'acct': 163,
    'settimeofday': 164, 'adjtimex': 159, 'clock_settime': 227, 'clock_adjtime': 305, 'personality': 135, 'userfaultfd': 323,
    'iopl': 172, 'ioperm': 173, 'syslog': 103, 'quotactl': 179, 'sethostname': 170, 'setdomainname': 171, 'vhangup': 153,
    'lookup_dcookie': 212, 'mount_setattr': 442, 'move_mount': 429, 'open_tree': 428, 'fsopen': 430, 'fsconfig': 431,
    'fsmount': 432, 'fspick': 433,
}
CLONE, CLONE3, SOCKET = 56, 435, 41
CLONE_NEW_MASK = 0x00020000 | 0x02000000 | 0x04000000 | 0x08000000 | 0x10000000 | 0x20000000 | 0x40000000
BLOCKED_SOCKET_DOMAINS = (2, 10, 16, 17)  # AF_INET, AF_INET6, AF_NETLINK, AF_PACKET


def build():
    p = []

    def ins(code, k, jt=0, jf=0):
        p.append((code, jt, jf, k))

    ins(LD_ABS_W, OFF_ARCH)
    ins(JEQ, AUDIT_ARCH_X86_64, 1, 0)
    ins(RET, RET_KILL_PROCESS)
    ins(LD_ABS_W, OFF_NR)
    ins(JGE, 0x40000000, 0, 1)            # x32 ABI numbers
    ins(RET, errno(ENOSYS))
    for nr in DENY.values():
        ins(JEQ, nr, 0, 1)
        ins(RET, errno(EPERM))
    ins(JEQ, CLONE3, 0, 1)
    ins(RET, errno(ENOSYS))
    # clone(2): refuse any namespace flag, allow the rest
    ins(JEQ, CLONE, 0, 5)
    ins(LD_ABS_W, OFF_ARG0)
    ins(JSET, CLONE_NEW_MASK, 0, 1)
    ins(RET, errno(EPERM))
    ins(LD_ABS_W, OFF_NR)
    ins(RET, RET_ALLOW)
    # socket(2): refuse network families, allow the rest (AF_UNIX etc.)
    skip = 1 + 2 * len(BLOCKED_SOCKET_DOMAINS) + 1
    ins(JEQ, SOCKET, 0, skip)
    ins(LD_ABS_W, OFF_ARG0)
    for d in BLOCKED_SOCKET_DOMAINS:
        ins(JEQ, d, 0, 1)
        ins(RET, errno(EPERM))
    ins(RET, RET_ALLOW)
    ins(RET, RET_ALLOW)
    return p


if __name__ == '__main__':
    program = build()
    assert len(program) < 4096
    with open(sys.argv[1], 'wb') as f:
        for code, jt, jf, k in program:
            f.write(struct.pack('=HBBI', code, jt, jf, k))
    print('wrote %d instructions' % len(program))
