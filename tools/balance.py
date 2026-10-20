#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 AcideFluorhydrique
# SPDX-License-Identifier: GPL-3.0-or-later
"""
開發者用的平衡分析工具（不是遊戲的一部分，玩家看不到）。

核心指標
--------
DPS  每秒傷害。範圍塔要乘上「同時打到幾隻」，否則會嚴重低估。
DPG  DPS per Gold = DPS / 累計投入金幣。這是塔與塔之間唯一公平的比較基準，
     業界（Bloons TD、Kingdom Rush 這類）內部調數值時用的就是這個。
     DPG 高 = 同樣的錢買到更多輸出。

可行性
------
敵人不是站著讓你打的，它走過一座塔的射程只有一小段弧線（chord）。
所以一波的可用傷害 ≈ Σ(塔DPS) × 交戰秒數，而交戰秒數 ≈ 生成跨度 + 弧線通過時間，
不是整個波次窗口。用這個模型往前推 8 波，看蘿蔔血夠不夠扣。

模型刻意不做的事（都是對玩家有利的方向，所以它說「不行」時可信，
說「可以」時只代表理論上限）：
  * 不模擬減速。冰塔／月亮拖慢敵人只會拉長「尾段」那一項，生成跨度不受影響，
    上限約 +20%；完全沒有減速塔的關卡（例如 5-4）連這點都拿不到。
  * 不算清障收入。障礙物動輒上千血，第一波根本來不及打掉換錢。
  * 擺位假設 100% 覆蓋路徑，真人做不到。

用法
----
    python3 tools/balance.py towers          # 塔的性價比表
    python3 tools/balance.py levels          # 所有關卡的可行性掃描
    python3 tools/balance.py levels hard     # 同上，困難模式
    python3 tools/balance.py level 15        # 單一關卡逐波拆解
    python3 tools/balance.py level 15 hard   # 同上，困難模式
    python3 tools/balance.py endless         # 無盡模式：逐波模擬，看在第幾波結束

Tower.kt 的公式改了，下面的 TOWERS 也要跟著改。
"""

import copy
import re
import sys
import math
from pathlib import Path

FPS = 60
MAX_LEVEL = 3
POISON_TICK_FRAMES = 18
PREP_FRAMES = 240

SRC = Path(__file__).resolve().parent.parent / "app/src/main/java/io/github/acidefluorhydrique/carrot"

# ---------------------------------------------------------------- 塔（Tower.kt 的鏡像）

TOWERS = {
    # name: (cost, damage(L), interval(L), rangeCells(L), kind)
    "ARROW":  (50,  lambda L: 3 + L * 3,  lambda L: max(18, 40 - L * 6),   lambda L: 2.4 + L * 0.3,  "single"),
    "ICE":    (60,  lambda L: 1 + L,      lambda L: max(24, 55 - L * 8),   lambda L: 2.0 + L * 0.3,  "single"),
    "BOMB":   (85,  lambda L: 4 + L * 5,  lambda L: max(44, 85 - L * 10),  lambda L: 1.9 + L * 0.3,  "splash"),
    "MOON":   (75,  lambda L: 1,          lambda L: max(46, 70 - L * 6),   lambda L: 2.7 + L * 0.35, "pulse"),
    "POISON": (95,  lambda L: 1 + L,      lambda L: max(40, 72 - L * 8),   lambda L: 2.0 + L * 0.25, "poison"),
    "ROCKET": (120, lambda L: 8 + L * 7,  lambda L: max(70, 120 - L * 13), lambda L: 3.2 + L * 0.4,  "pierce"),
    "LIGHT":  (130, lambda L: 3 + L * 3,  lambda L: max(30, 62 - L * 9),   lambda L: 2.5 + L * 0.35, "chain"),
    "SUN":    (110, lambda L: 2 + L * 2,  lambda L: max(40, 66 - L * 7),   lambda L: 2.7 + L * 0.35, "pulse"),
}

UNLOCK_AT = {"ARROW": 1, "ICE": 2, "BOMB": 5, "MOON": 7, "POISON": 9,
             "ROCKET": 11, "LIGHT": 13, "SUN": 15}

SPLASH_CELLS = lambda L: 1.2 + L * 0.2          # Tower.splashRadius
CHAIN_TARGETS = lambda L: 1 + L                 # Tower.chainTargets

# 穿透彈打出去之後不受射程限制，會一路飛到出界。它到底掃到幾隻，
# 取決於彈道有沒有對齊一段直路 —— 這是整個模型裡唯一跟擺位有關的假設。
# 6 格 = 擺位普通的玩家大致能對齊的直路長度；擺得好可以到 10 格以上。
PIERCE_CORRIDOR_CELLS = 6.0


def upgrade_cost(name, level):
    """Tower.kt: ((baseCost * 1.15 * level) / 5).toInt() * 5"""
    return int((TOWERS[name][0] * 1.15 * level) / 5) * 5


def total_cost(name, level):
    """蓋起來並升到 level 的累計投入。"""
    cost = TOWERS[name][0]
    for l in range(1, level):
        cost += upgrade_cost(name, l)
    return cost


def chord_cells(name, level):
    """塔擺在離路徑 1 格處，敵人穿過射程圓的弦長（格）。"""
    r = TOWERS[name][3](level)
    return 2 * math.sqrt(max(r * r - 1.0, 0.04))


def coverage_cells(name, level):
    """一次攻擊能掃到多長的一段路徑。穿透彈不受射程圓限制。"""
    if TOWERS[name][4] == "pierce":
        return PIERCE_CORRIDOR_CELLS
    return chord_cells(name, level)


def dps(name, level, armor=0, density=1.0):
    """
    每秒傷害。density = 射程內同時存在幾隻敵人，
    只有能一次打多個目標的塔吃得到這個加成。
    """
    _, dmg_f, itv_f, _, kind = TOWERS[name]
    dmg = dmg_f(level)
    itv = itv_f(level)
    hit = max(1, dmg - armor)          # Enemy.takeDamage: 最少造成 1 點

    if kind == "poison":
        # 命中傷害 + 毒 DoT。毒無視護甲、不疊加，單體上視為常駐
        return hit * FPS / itv + (1 + level) * FPS / POISON_TICK_FRAMES
    if kind == "chain":
        return hit * min(density, CHAIN_TARGETS(level)) * FPS / itv
    if kind == "splash":
        # 濺射半徑內的敵人，但至少打中主目標
        return hit * max(1.0, min(density, 2 * SPLASH_CELLS(level))) * FPS / itv
    if kind in ("pulse", "pierce"):
        return hit * max(1.0, density) * FPS / itv
    return hit * FPS / itv


def dpg(name, level, armor=0, density=1.0):
    return dps(name, level, armor, density) / total_cost(name, level)


# ---------------------------------------------------------------- 關卡（解析 GameLevel.kt）

KIND_STATS = {
    "GRUNT":  (1.0,  1.0,  1.0,  0, 1),
    "RUNNER": (0.6,  1.55, 0.95, 0, 1),
    "SWARM":  (0.42, 1.2,  0.5,  0, 1),
    "TANK":   (2.6,  0.62, 1.7,  3, 2),
    "BOSS":   (9.0,  0.52, 6.0,  6, 5),
}

OBSTACLE = {  # kind: (hpFactor, goldFactor)
    "MUSHROOM": (0.5, 1.2), "ROCK": (1.0, 1.8), "TREE": (1.5, 2.4),
    "ICE_BLOCK": (1.1, 1.6), "TOXIC": (0.6, 1.2), "CRATE": (2.6, 8.0),
}


class Group:
    def __init__(self, kind, count, hp, speed, reward, interval):
        self.kind, self.count, self.interval = kind, count, interval
        m = KIND_STATS[kind]
        self.hp = max(1, int(hp * m[0]))
        self.speed = speed * m[1]              # 格/秒（Enemy.speed 已對格寬正規化）
        self.reward = max(1, int(reward * m[2]))
        self.armor, self.leak = m[3], m[4]
        self.raw_hp, self.raw_reward = hp, reward

    @property
    def total_hp(self):
        return self.hp * self.count


class Level:
    def __init__(self, lid, chapter, index, gold, chp, path_len, obstacles, allowed, waves):
        self.id, self.chapter, self.index = lid, chapter, index
        self.start_gold, self.carrot_hp, self.path_len = gold, chp, path_len
        self.obstacles, self.allowed, self.waves = obstacles, allowed, waves

    @property
    def name(self):
        return f"{self.chapter}-{self.index}"

    @property
    def usable(self):
        """開放清單 ∩ 解鎖進度。"""
        return [t for t in self.allowed if UNLOCK_AT[t] <= self.id]

    def obstacle_gold(self):
        if not self.waves:
            return 0
        scale = self.waves[0][0].raw_reward * (1 + self.id * 0.12)
        return sum(max(3, int(scale * OBSTACLE[k][1])) for k in self.obstacles)

    def obstacle_hp(self):
        if not self.waves:
            return 0
        scale = self.waves[0][0].raw_hp * 12
        return sum(max(8, int(scale * OBSTACLE[k][0])) for k in self.obstacles)


def parse_levels():
    text = (SRC / "GameLevel.kt").read_text(encoding="utf-8")
    levels = []
    for b in re.split(r"\n        LevelConfig\(", text)[1:]:
        def num(key):
            m = re.search(rf"\b{key}\s*=\s*(-?\d+)", b)
            return int(m.group(1)) if m else 0

        pm = re.search(r"path = listOf\((.*?)\)\s*,\s*\n\s*permanent", b, re.S)
        path_len = len(re.findall(r"\d+\s+to\s+\d+", pm.group(1))) if pm else 0
        obstacles = re.findall(r"ObstacleSpec\(\s*\d+\s*,\s*\d+\s*,\s*ObstacleKind\.(\w+)", b)
        am = re.search(r"allowedTowers = listOf\((.*?)\)", b, re.S)
        allowed = re.findall(r"TowerType\.(\w+)", am.group(1)) if am else []

        waves = []
        wm = re.search(r"waves = listOf\((.*)", b, re.S)
        if wm:
            for wb in re.split(r"WaveConfig\(", wm.group(1))[1:]:
                groups = [Group(g[0], int(g[1]), int(g[2]), float(g[3]), int(g[4]), int(g[5]))
                          for g in re.findall(
                              r"WaveGroup\(EnemyKind\.(\w+),\s*(\d+),\s*(\d+),\s*([\d.]+)f,\s*(\d+),\s*(\d+)\)", wb)]
                if groups:
                    waves.append(groups)
        levels.append(Level(num("id"), num("chapterId"), num("indexInChapter"),
                            num("startGold"), num("carrotHp"), path_len, obstacles, allowed, waves))
    return levels


# ---------------------------------------------------------------- 前推模擬

def pick_build(usable, gold, armor, spacing, count_cap):
    """
    逐座買：每次挑「當下買得起、DPG 最高」的一座，直到錢不夠為止。

    一次決定一種塔的舊寫法會低估玩家 —— 最佳選項買不起就整筆放棄，
    或是買完幾座之後剩下的錢湊不出一整座就當作浪費掉。真人會混搭。

    回傳 [(塔, 等級, dps, 掃描長度), ...]。
    """
    remaining = gold
    build = []
    while True:
        best = None
        for t in usable:
            for lv in range(1, MAX_LEVEL + 1):
                c = total_cost(t, lv)
                if c > remaining:
                    continue
                d = max(1.0, min(count_cap, coverage_cells(t, lv) / spacing))
                v = dpg(t, lv, armor, d)
                if best is None or v > best[0]:
                    best = (v, t, lv, c, dps(t, lv, armor, d))
        if best is None:
            break
        _, t, lv, c, tower_dps = best
        remaining -= c
        build.append((t, lv, tower_dps, coverage_cells(t, lv)))
        if len(build) > 200:            # 保險，避免出現零成本塔時空轉
            break
    return build


def call_bonus(wave_number, rest_frames):
    """EnemyManager.callBonus：提前叫下一波的獎勵金。好玩家一定會拿。"""
    return 20 + wave_number * 6 + rest_frames // 8


def simulate(level, coverage=1.0):
    """
    逐波前推。coverage 是「塔實際罩得到路徑的比例」，1.0 是理想擺位。
    回傳每波的 (波次, 總血, 可用金, 交戰秒, 可輸出傷害, 覆蓋率, 漏怪, 剩餘蘿蔔血)
    """
    usable = level.usable
    if not usable or not level.waves:
        return None

    gold = level.start_gold
    carrot = level.carrot_hp
    rows = []

    for wi, groups in enumerate(level.waves, 1):
        wave_hp = sum(g.total_hp for g in groups)
        delivered = 0.0
        engaged_total = 0.0
        leaked_damage = 0

        # 提前叫波的獎勵金：第一波備戰 240 幀，之後每波間隔 restFrames
        budget = gold + call_bonus(wi, PREP_FRAMES if wi == 1 else 110)

        for g in groups:
            # 敵人在路徑上的間距（格），決定範圍塔一次能掃到幾隻
            spacing = max(g.speed * g.interval / FPS, 0.3)
            spawn_span = (g.count - 1) * g.interval / FPS

            build = pick_build(usable, budget, g.armor, spacing, g.count)
            # 每座塔各自算交戰時間：射程大的塔尾巴拖得比較長
            best_engaged = 0.0
            for _, _, tower_dps, cover in build:
                engaged = spawn_span + cover / g.speed
                delivered += tower_dps * engaged * coverage
                best_engaged = max(best_engaged, engaged)
            engaged_total += best_engaged

        # 傷害按順序吃掉敵人，殺不完的漏出去
        remaining = delivered
        killed_gold = 0
        for g in groups:
            k = min(g.count, int(remaining // g.hp))
            remaining -= k * g.hp
            killed_gold += k * g.reward
            leaked_damage += (g.count - k) * g.leak

        gold += killed_gold
        carrot -= leaked_damage
        ratio = delivered / wave_hp if wave_hp else float("inf")
        rows.append((wi, wave_hp, gold - killed_gold, engaged_total, delivered, ratio,
                     leaked_damage, carrot))
        if carrot <= 0:
            break

    return rows, usable


# ---------------------------------------------------------------- 輸出

def cmd_towers():
    print("塔的性價比。DPG = DPS / 累計金幣，越高越划算。")
    print("density=1 是單體目標，density=3 模擬敵人排隊時的範圍塔表現。\n")
    hdr = (f"{'塔':<8}{'Lv':<4}{'累計金':>7}{'射程':>6}{'弦長':>6}"
           f"{'掃長':>6}{'DPS':>8}{'DPG':>8}{'DPS@d3':>9}{'DPG@d3':>9}{'DPG@甲3':>9}")
    print(hdr)
    print("-" * 80)
    for name in TOWERS:
        for lv in range(1, MAX_LEVEL + 1):
            c = total_cost(name, lv)
            print(f"{name:<8}{lv:<4}{c:>7}{TOWERS[name][3](lv):>6.2f}{chord_cells(name,lv):>6.2f}"
                  f"{coverage_cells(name,lv):>6.2f}"
                  f"{dps(name,lv):>8.2f}{dpg(name,lv):>8.4f}"
                  f"{dps(name,lv,density=3):>9.2f}{dpg(name,lv,density=3):>9.4f}"
                  f"{dpg(name,lv,armor=3):>9.4f}")
        print()
    print("基準線：ARROW Lv1 的 DPG = %.4f。低於它的塔，等於玩家花更多錢買到更少輸出。"
          % dpg("ARROW", 1))


# ---------------------------------------------------------------- 難度（對應 Difficulty.kt）

# Difficulty.HARD 的參數。Difficulty.kt 改了，這裡也要跟著改。
HARD_HP_GROWTH = 1.15
HARD_COUNT_GROWTH = 1.08
MIN_SPAWN_INTERVAL = 8


def kround(x):
    """Kotlin 的 roundToInt：.5 一律往上。Python 的 round 是銀行家捨入，會差一。"""
    return int(math.floor(x + 0.5))


def harden(level):
    """
    Difficulty.HARD.apply 的 Python 版本：第 w 波（0 起算）的血量乘 HARD_HP_GROWTH^w，
    數量乘 HARD_COUNT_GROWTH^w，生成間隔同比縮短；首領不放大數量，金幣不動。
    """
    lv = copy.deepcopy(level)
    for w, groups in enumerate(lv.waves):
        fh = HARD_HP_GROWTH ** w
        fc = HARD_COUNT_GROWTH ** w
        for g in groups:
            g.raw_hp = max(1, kround(g.raw_hp * fh))
            g.hp = max(1, int(g.raw_hp * KIND_STATS[g.kind][0]))
            if g.kind == "BOSS":
                continue
            n = max(1, kround(g.count * fc))
            g.interval = max(MIN_SPAWN_INTERVAL, kround(g.interval * g.count / n))
            g.count = n
    return lv


def levels_for(difficulty):
    levels = parse_levels()
    return [harden(lv) for lv in levels] if difficulty == "hard" else levels


def cmd_levels(difficulty="normal"):
    if difficulty == "hard":
        print(f"困難模式：血量 ×{HARD_HP_GROWTH}^w，數量 ×{HARD_COUNT_GROWTH}^w（w 為 0 起算的波次）\n")
    print("關卡可行性掃描。ratio = 可輸出傷害 / 該波總血量。")
    print("ratio < 1.0 表示這一波不可能清乾淨；看『剩餘蘿蔔血』是否歸零。\n")
    print(f"{'關卡':<7}{'可用塔':<36}{'最低ratio':>10}{'總漏怪傷':>10}{'蘿蔔血':>9}{'結果':>8}")
    print("-" * 82)
    for lv in levels_for(difficulty):
        res = simulate(lv)
        if not res:
            continue
        rows, usable = res
        worst = min(r[5] for r in rows)
        leaked = sum(r[6] for r in rows)
        final = rows[-1][7]
        ok = "OK" if final > 0 and len(rows) == len(lv.waves) else "失敗"
        mark = "" if ok == "OK" else "  ⚠️"
        print(f"{lv.name:<7}{','.join(usable):<36}{worst:>10.2f}{leaked:>10}"
              f"{final:>9}{ok:>8}{mark}")


def cmd_level(lid, difficulty="normal"):
    lv = next(l for l in levels_for(difficulty) if l.id == lid)
    rows, usable = simulate(lv)
    print(f"=== {lv.name} (id={lv.id}) ===")
    print(f"起始金 {lv.start_gold} / 蘿蔔血 {lv.carrot_hp} / 路徑 {lv.path_len} 格")
    print(f"障礙物: {len(lv.obstacles)} 個，共 {lv.obstacle_hp()} HP，清光可得 {lv.obstacle_gold()} 金")
    print(f"可用塔: {', '.join(usable)}\n")
    print(f"{'波':<4}{'總血量':>8}{'開場金':>8}{'交戰秒':>8}{'可輸出':>9}{'ratio':>7}{'漏怪傷':>8}{'蘿蔔':>7}")
    print("-" * 60)
    for wi, hp, gold, eng, dmg, ratio, leak, carrot in rows:
        mark = "  ⚠️" if ratio < 1.0 else ""
        print(f"{wi:<4}{hp:>8}{gold:>8}{eng:>8.1f}{dmg:>9.0f}{ratio:>7.2f}{leak:>8}{carrot:>7}{mark}")
    print()
    print("該關可用塔（density=3 的實戰值）:")
    for t in usable:
        b = max(range(1, MAX_LEVEL + 1), key=lambda l: dpg(t, l, density=3))
        print(f"  {t:<8} Lv{b} 花費{total_cost(t,b):>4}  DPS={dps(t,b,density=3):>6.2f}"
              f"  DPG={dpg(t,b,density=3):.4f}  射程={TOWERS[t][3](b):.2f}格")
    print(f"\n  對照 ARROW Lv1: DPS={dps('ARROW',1,density=3):.2f} "
          f"DPG={dpg('ARROW',1,density=3):.4f} 花費50")


# ---------------------------------------------------------------- 無盡模式（對應 Endless.kt）

# Endless.kt 改了，這裡也要跟著改。
ENDLESS_START_GOLD = 350
ENDLESS_CARROT_HP = 10
ENDLESS_BASE_HP = 30
ENDLESS_HP_GROWTH = 1.12
ENDLESS_BASE_COUNT = 10
ENDLESS_COUNT_GROWTH = 1.03
ENDLESS_BASE_REWARD = 6
ENDLESS_REWARD_GROWTH = 1.06
ENDLESS_BASE_SPEED = 1.5
ENDLESS_SPEED_STEP = 0.01
ENDLESS_MAX_SPEED = 2.1
ENDLESS_MAX_GROUP = 40
ENDLESS_MAX_HP = 200_000_000
ENDLESS_PATTERN = [
    [("GRUNT", 1.0)],
    [("RUNNER", 1.0)],
    [("SWARM", 1.0)],
    [("GRUNT", 0.6), ("RUNNER", 0.6)],
    [("TANK", 1.0)],
    [("SWARM", 0.7), ("GRUNT", 0.5)],
    [("RUNNER", 0.7), ("TANK", 0.6)],
    [("SWARM", 1.3)],
    [("TANK", 0.7), ("GRUNT", 0.6)],
    [("BOSS", 1.0), ("GRUNT", 0.7)],
]
ENDLESS_COUNT_FACTOR = {"SWARM": 2.2, "TANK": 0.45}
ENDLESS_INTERVAL = {"GRUNT": 42, "RUNNER": 32, "SWARM": 14, "TANK": 56, "BOSS": 150}


def endless_wave(index):
    """Endless.build 的 Python 版本。"""
    hp = max(1, kround(min(ENDLESS_BASE_HP * ENDLESS_HP_GROWTH ** index, ENDLESS_MAX_HP)))
    count = ENDLESS_BASE_COUNT * ENDLESS_COUNT_GROWTH ** index
    reward = max(1, kround(ENDLESS_BASE_REWARD * ENDLESS_REWARD_GROWTH ** index))
    speed = min(ENDLESS_BASE_SPEED + ENDLESS_SPEED_STEP * index, ENDLESS_MAX_SPEED)
    groups = []
    for kind, weight in ENDLESS_PATTERN[index % len(ENDLESS_PATTERN)]:
        if kind == "BOSS":
            n = 1
        else:
            n = min(ENDLESS_MAX_GROUP, max(1, kround(count * weight * ENDLESS_COUNT_FACTOR.get(kind, 1.0))))
        groups.append(Group(kind, n, hp, speed, reward, ENDLESS_INTERVAL[kind]))
    return groups


def capped_build(usable, gold, armor, spacing, count_cap, slots):
    """
    有格子上限的建塔。pick_build 不限塔數，只看每金幣的 DPS，所以永遠只蓋 1 級塔；
    格子有限時，到了某個點升級才是唯一能花錢的地方。這裡每一步在「蓋新塔」和
    「升級既有的塔」之間挑每金幣多出最多 DPS 的那一個。
    """
    def tower_dps(t, lv):
        d = max(1.0, min(count_cap, coverage_cells(t, lv) / spacing))
        return dps(t, lv, armor, d)

    towers = []
    remaining = gold
    while True:
        best = None
        if len(towers) < slots:
            for t in usable:
                c = total_cost(t, 1)
                if c <= remaining:
                    v = tower_dps(t, 1) / c
                    if best is None or v > best[0]:
                        best = (v, "build", t, c)
        for i, (t, lv) in enumerate(towers):
            if lv >= MAX_LEVEL:
                continue
            c = total_cost(t, lv + 1) - total_cost(t, lv)
            if c <= remaining:
                v = (tower_dps(t, lv + 1) - tower_dps(t, lv)) / c
                if best is None or v > best[0]:
                    best = (v, "upgrade", i, c)
        if best is None:
            return towers
        _, action, ref, c = best
        remaining -= c
        if action == "build":
            towers.append((ref, 1))
        else:
            towers[ref] = (towers[ref][0], towers[ref][1] + 1)


def simulate_endless(slots, max_waves=200, rows=None):
    """逐波前推到蘿蔔歸零，回傳結束的波次。rows 給一個 list 就會收集每波的明細。"""
    usable = list(TOWERS)
    gold = ENDLESS_START_GOLD
    carrot = ENDLESS_CARROT_HP
    for w in range(max_waves):
        groups = endless_wave(w)
        budget = gold + call_bonus(w + 1, PREP_FRAMES if w == 0 else 110)
        delivered = 0.0
        built = 0
        for g in groups:
            spacing = max(g.speed * g.interval / FPS, 0.3)
            span = (g.count - 1) * g.interval / FPS
            towers = capped_build(usable, budget, g.armor, spacing, g.count, slots)
            built = max(built, len(towers))
            for t, lv in towers:
                d = max(1.0, min(g.count, coverage_cells(t, lv) / spacing))
                delivered += dps(t, lv, g.armor, d) * (span + coverage_cells(t, lv) / g.speed)
        remaining = delivered
        earned = leaked = 0
        for g in groups:
            k = min(g.count, int(remaining // g.hp))
            remaining -= k * g.hp
            earned += k * g.reward
            leaked += (g.count - k) * g.leak
        total = sum(g.total_hp for g in groups)
        gold += earned
        carrot -= leaked
        if rows is not None:
            rows.append((w + 1, total, budget, built, delivered / total, leaked, carrot))
        if carrot <= 0:
            return w + 1
    return max_waves


def cmd_endless():
    print("無盡模式。玩家上限是格子數；格子填滿之後只能升級，血量遲早追上輸出。")
    print("不模擬核彈、減速與地形，偏保守。\n")
    slots = 40
    rows = []
    end = simulate_endless(slots, rows=rows)
    print(f"格子上限 {slots} 座：")
    print(f"{'波':>4}{'總血量':>10}{'可用金':>9}{'塔數':>6}{'ratio':>8}{'漏怪傷':>7}{'蘿蔔':>6}")
    print("-" * 50)
    for wave, total, budget, built, ratio, leaked, carrot in rows:
        if wave <= 10 or wave % 5 == 0 or leaked:
            mark = "  ⚠️" if ratio < 1.0 else ""
            print(f"{wave:>4}{total:>10}{budget:>9}{built:>6}{ratio:>8.2f}{leaked:>7}{carrot:>6}{mark}")
    print(f"\n→ 第 {end} 波結束\n")
    print("格子上限的影響（越不敏感，代表結局越是由血量成長決定）：")
    for s in (25, 30, 40, 50, 60):
        print(f"  {s:>2} 座 → 第 {simulate_endless(s)} 波")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "towers"
    if cmd == "towers":
        cmd_towers()
    elif cmd == "levels":
        cmd_levels(sys.argv[2] if len(sys.argv) > 2 else "normal")
    elif cmd == "level":
        cmd_level(int(sys.argv[2]), sys.argv[3] if len(sys.argv) > 3 else "normal")
    elif cmd == "endless":
        cmd_endless()
    else:
        print(__doc__)
