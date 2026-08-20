#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 AcideFluorhydrique
# SPDX-License-Identifier: GPL-3.0-or-later
"""
顏色重構的安全網。

比對「工作區」與某個 git 版本之間，每個檔案引用到的 ARGB 值集合是否一致。
畫面沒有自動化測試，所以搬動顏色時至少要能保證值本身沒被改掉。

抓得到：打錯十六進位、漏掉一處、重複貼上。
抓不到：把 A 處的顏色換成 B 處的顏色（集合相同、位置不同）——那只能靠眼睛。

    python3 tools/check_colors.py [git-ref]      # 預設 HEAD
"""
import collections
import re
import subprocess
import sys

SRC = "app/src/main/java/io/github/acidefluorhydrique/carrot"
CALL = re.compile(r"(?:Color\.parseColor|Colors\.of)\(\s*([^)]+?)\s*\)")


def argb(token):
    """"#RRGGBB" / "#AARRGGBB" 正規化成 32-bit ARGB；非字面量原樣保留。"""
    m = re.fullmatch(r'"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})"', token)
    if not m:
        return token.strip()
    h = m.group(1)
    return "0x" + ("FF" + h if len(h) == 6 else h).upper()


def extract(text):
    return collections.Counter(argb(t) for t in CALL.findall(text))


def main(ref="HEAD"):
    files = subprocess.run(["git", "ls-files", SRC],
                           capture_output=True, text=True).stdout.split()
    bad = 0
    for f in files:
        old = subprocess.run(["git", "show", f"{ref}:{f}"],
                             capture_output=True, text=True).stdout
        if not old:
            continue                      # 這個版本還沒有這個檔案
        new = open(f, encoding="utf-8").read()
        before, after = extract(old), extract(new)
        if before != after:
            print(f"❌ {f.split('/')[-1]}")
            for k in sorted(set(before) | set(after)):
                if before[k] != after[k]:
                    print(f"     {k}: {before[k]} -> {after[k]}")
            bad += 1
    print("✅ 顏色值集合與 %s 一致" % ref if bad == 0 else f"❌ {bad} 個檔案有差異")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "HEAD"))
