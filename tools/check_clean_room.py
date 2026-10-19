#!/usr/bin/env python3
"""净室检查：新仓里有没有哪一段是照抄前仓的。

判据：把两边的 .kt 都归一化（去空行、去只剩括号/花括号的行、压缩空白），
连续 6 行完全相同即算污染。6 行是我拍的：Compose 的 `Modifier.fillMaxSize()`
这类样板很容易撞，但撞满 6 行连续相同，就不是巧合了。

用法（在 andaxin 目录下）：
    python tools/check_clean_room.py ../lightxin-main beta
    python tools/check_clean_room.py ../lightxin-main beta --min 8

退出码：0 干净，1 有污染，2 参数或环境错。只用标准库。
"""
import argparse
import os
import subprocess
import sys

BRACE_ONLY = {"{", "}", "})", "}", "};", "(", ")", "},", "}", "else {", "});"}


def normalize(lines):
    out = []
    for idx, raw in enumerate(lines):
        s = raw.strip()
        if not s or s in BRACE_ONLY or s in ("{", "}"):
            continue
        # import / package 不算表达式：那是 Compose 的固定词汇表，任何一屏都会撞满 6 行，
        # 留着它这个检查只会报噪音。真正要比的是写出来的东西。
        if s.startswith("import ") or s.startswith("package "):
            continue
        out.append((idx + 1, " ".join(s.split())))
    return out


def read_git_file(repo, ref, path):
    try:
        data = subprocess.check_output(
            ["git", "-C", repo, "show", "%s:%s" % (ref, path)],
            stderr=subprocess.DEVNULL,
        )
        return data.decode("utf-8", "replace").splitlines()
    except subprocess.CalledProcessError:
        return []


def git_ls_kt(repo, ref):
    out = subprocess.check_output(
        ["git", "-C", repo, "ls-tree", "-r", "--name-only", ref],
        text=True,
    ).splitlines()
    return [p for p in out if p.endswith(".kt")]


def walk_local_kt(root):
    found = []
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle")]
        for f in files:
            if f.endswith(".kt"):
                found.append(os.path.join(dirpath, f))
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("old_repo")
    ap.add_argument("ref", nargs="?", default="beta")
    ap.add_argument("--min", type=int, default=6, help="连续多少行算污染（默认 6）")
    ap.add_argument("--new-root", default=None, help="默认取脚本所在仓的 app 目录")
    a = ap.parse_args()

    new_root = a.new_root or os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app")
    new_files = walk_local_kt(new_root)
    if not new_files:
        print("新仓里一个 .kt 都没找到：%s" % new_root)
        return 2

    n = a.min
    # 前仓所有 .kt 的 n 行窗口 → 出处
    windows = {}
    old_paths = git_ls_kt(a.old_repo, a.ref)
    for p in old_paths:
        norm = normalize(read_git_file(a.old_repo, a.ref, p))
        for i in range(len(norm) - n + 1):
            key = "\n".join(t for _, t in norm[i:i + n])
            windows.setdefault(key, "%s:%d" % (p, norm[i][0]))

    print("前仓 %s@%s：%d 个文件，%d 个 %d 行窗口" % (a.old_repo, a.ref, len(old_paths), len(windows), n))

    hits = []
    for path in new_files:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            norm = normalize(fh.read().splitlines())
        i = 0
        while i + n <= len(norm):
            key = "\n".join(t for _, t in norm[i:i + n])
            src = windows.get(key)
            if src:
                hits.append((os.path.relpath(path, new_root), norm[i][0], n, src))
                i += n
            else:
                i += 1

    if not hits:
        print("干净：%d 个新文件里没有任何连续 %d 行与前仓相同" % (len(new_files), n))
        return 0

    print("污染 %d 处：" % len(hits))
    for f, line, run, src in hits:
        print("  %s:%d  连续 %d 行  ← %s" % (f, line, run, src))
    return 1


if __name__ == "__main__":
    sys.exit(main())
