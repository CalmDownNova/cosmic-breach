"""A line-by-line Python port of the parts of playerAnimator 2.0.4 (KosmX,
jar playeranimator-2.0.4+1.21.1-forge) that decide which pose a player is in.

Ported from the decompiled jar (Vineflower 1.10.1):
  core.data.gson.AnimationJson       the "emotecraft" JSON reader (tried first)
  core.data.gson.GeckoLibSerializer  the GeckoLib/Blockbench JSON reader
  core.data.KeyframeAnimation        builder defaults, State.findAtTick/addKeyFrame/optimize
  api.layered.KeyframeAnimationPlayer  tick(), Axis.getValueAtCurrentTick, easing choice
  core.util.Ease / Easing / MathHelper
  api.layered.modifier.AbstractFadeModifier, MirrorModifier, SpeedModifier

Nothing here is guessed: when the library does something odd (easing taken
from the keyframe *before* a segment, CONSTANT for GeckoLib array keyframes,
"easingArg" crashing the emotecraft reader, catmullrom being broken) this file
does the same odd thing, so the previewer shows what the game will show.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from typing import Callable, Optional

import numpy as np

PI = math.pi
F32 = np.float32

POSITION, ROTATION, SCALE, BEND = "position", "rotation", "scale", "bend"

# ----------------------------------------------------------------------------- math


def clamp_to_radian(f: float) -> float:
    """MathHelper.clampToRadian: wraps into [-pi, pi)."""
    b = math.fmod(f + PI, 2 * PI)  # Java % on doubles keeps the dividend's sign, like fmod
    if b < 0.0:
        b += 2 * PI
    return b - PI


def lerp(delta: float, start: float, end: float) -> float:
    return start + delta * (end - start)


# ----------------------------------------------------------------------------- easing (core.util.Ease / Easing)


def _sine(n):
    return 1.0 - math.cos(n * PI / 2.0)


def _back(arg):
    n2 = 1.70158 if arg is None else arg * 1.70158
    return lambda t: t * t * ((n2 + 1.0) * t - n2)


def _elastic(arg):
    n2 = 1.0 if arg is None else arg
    return lambda t: 1.0 - math.pow(math.cos(t * PI / 2.0), 3.0) * math.cos(t * n2 * PI)


def _bounce(arg):
    n2 = 0.5 if arg is None else arg
    one = lambda x: 7.5625 * x * x
    two = lambda x: 30.25 * n2 * (x - 0.54545456) ** 2 + 1.0 - n2
    three = lambda x: 121.0 * n2 * n2 * (x - 0.8181818) ** 2 + 1.0 - n2 * n2
    four = lambda x: 484.0 * n2 * n2 * n2 * (x - 0.95454544) ** 2 + 1.0 - n2 * n2 * n2
    return lambda t: min(min(one(t), two(t)), min(three(t), four(t)))


def _step(arg):
    n2 = 2.0 if arg is None else arg
    if n2 < 2.0:
        raise ValueError("Steps must be >= 2")
    steps = int(n2)

    def f(t):
        if t < 0.0:
            return 0.0
        step_len = 1.0 / steps
        last = (steps - 1) * step_len
        if t > last:
            return last
        lo, hi = 0, steps - 1
        while hi - lo != 1:
            mid = lo + (hi - lo) // 2
            if t >= mid * step_len:
                lo = mid
            else:
                hi = mid
        return lo * step_len

    return f


def _catmullrom(n):
    # Easing.catmullRom simplifies to n + 2: CATMULLROM is broken in this version.
    return 0.5 * (2.0 * (n + 1.0) + (n + 2.0 - n) + (2.0 * n - 5.0 * (n + 1.0) + 4.0 * (n + 2.0) - (n + 3.0))
                  + (3.0 * (n + 1.0) - n - 3.0 * (n + 2.0) + n + 3.0))


def _in(fn):
    return fn


def _out(fn):
    return lambda t: 1.0 - fn(1.0 - t)


def _inout(fn):
    return lambda t: fn(t * 2.0) / 2.0 if t < 0.5 else 1.0 - fn((1.0 - t) * 2.0) / 2.0


_EASE_IMPL: dict[str, Callable[[Optional[float]], Callable[[float], float]]] = {
    "LINEAR": lambda a: _in(lambda f: f),
    "CONSTANT": lambda a: _in(lambda f: 0.0),
    "INSINE": lambda a: _in(_sine), "OUTSINE": lambda a: _out(_sine), "INOUTSINE": lambda a: _inout(_sine),
    "INCUBIC": lambda a: _in(lambda n: n ** 3), "OUTCUBIC": lambda a: _out(lambda n: n ** 3),
    "INOUTCUBIC": lambda a: _inout(lambda n: n ** 3),
    "INQUAD": lambda a: _in(lambda n: n * n), "OUTQUAD": lambda a: _out(lambda n: n * n),
    "INOUTQUAD": lambda a: _inout(lambda n: n * n),
    "INQUART": lambda a: _in(lambda n: n ** 4), "OUTQUART": lambda a: _out(lambda n: n ** 4),
    "INOUTQUART": lambda a: _inout(lambda n: n ** 4),
    "INQUINT": lambda a: _in(lambda n: n ** 5), "OUTQUINT": lambda a: _out(lambda n: n ** 5),
    "INOUTQUINT": lambda a: _inout(lambda n: n ** 5),
    "INEXPO": lambda a: _in(lambda n: 2.0 ** (10.0 * (n - 1.0))),
    "OUTEXPO": lambda a: _out(lambda n: 2.0 ** (10.0 * (n - 1.0))),
    "INOUTEXPO": lambda a: _inout(lambda n: 2.0 ** (10.0 * (n - 1.0))),
    "INCIRC": lambda a: _in(lambda n: 1.0 - math.sqrt(max(0.0, 1.0 - n * n))),
    "OUTCIRC": lambda a: _out(lambda n: 1.0 - math.sqrt(max(0.0, 1.0 - n * n))),
    "INOUTCIRC": lambda a: _inout(lambda n: 1.0 - math.sqrt(max(0.0, 1.0 - n * n))),
    "INBACK": lambda a: _in(_back(a)), "OUTBACK": lambda a: _out(_back(a)), "INOUTBACK": lambda a: _inout(_back(a)),
    "INELASTIC": lambda a: _in(_elastic(a)), "OUTELASTIC": lambda a: _out(_elastic(a)),
    "INOUTELASTIC": lambda a: _inout(_elastic(a)),
    "INBOUNCE": lambda a: _in(_bounce(a)), "OUTBOUNCE": lambda a: _out(_bounce(a)),
    "INOUTBOUNCE": lambda a: _inout(_bounce(a)),
    "CATMULLROM": lambda a: _inout(_catmullrom),
    "STEP": lambda a: _in(_step(a)),
}
EASE_NAMES = tuple(_EASE_IMPL)


def ease_from_string(s: str) -> str:
    """Easing.easeFromString: optional 'EASE' prefix, case-insensitive, anything else is LINEAR."""
    try:
        if len(s) < 4:
            raise ValueError  # Java substring(0, 4) throws, which the library turns into LINEAR
        if s[:4].upper() == "EASE":
            s = s[4:]
        name = s.upper()
        if name not in _EASE_IMPL:
            raise ValueError
        return name
    except Exception:
        return "LINEAR"


def ease_invoke(name: str, t: float, arg: Optional[float] = None) -> float:
    return _EASE_IMPL[name](arg)(t)


# ----------------------------------------------------------------------------- data model (core.data.KeyframeAnimation)


@dataclass
class KeyFrame:
    tick: int
    value: float
    ease: str = "INOUTSINE"  # the 2-argument Java constructor defaults to INOUTSINE
    arg: Optional[float] = None


class State:
    def __init__(self, name: str, default: float, threshold: float, is_angle: bool):
        self.name = name
        self.default = default
        self.threshold = threshold
        self.is_angle = is_angle
        self.frames: list[KeyFrame] = []
        self.enabled = False

    def find_at_tick(self, tick: int) -> int:
        """State.findAtTick, including Collections.binarySearch's exact probe order."""
        lo, hi = 0, len(self.frames) - 1
        found = None
        while lo <= hi:
            mid = (lo + hi) >> 1
            t = self.frames[mid].tick
            if t < tick:
                lo = mid + 1
            elif t > tick:
                hi = mid - 1
            else:
                found = mid
                break
        i = found if found is not None else -(lo + 1)
        if i < 0:
            i = -i - 2
        if i + 1 < len(self.frames) and self.frames[i + 1].tick == tick:
            return i + 1
        return i

    def add_keyframe(self, tick: int, value: float, ease: str, rotate: int = 0, degrees: bool = False,
                     arg: Optional[float] = None):
        if degrees and self.is_angle:
            value = value * (PI / 180.0)
        self._add(KeyFrame(tick, value, ease, arg))
        if self.is_angle and rotate != 0:
            self._add(KeyFrame(tick, value + 2 * PI * rotate, ease, arg))

    def _add(self, kf: KeyFrame):
        if math.isnan(kf.value):
            raise ValueError("value can't be NaN")
        self.enabled = True
        i = self.find_at_tick(kf.tick) + 1
        self.frames.insert(i, kf)

    def optimize(self, is_looped: bool, ret: int):
        i = 1
        while i < len(self.frames) - 1:
            a, b = self.frames[i - 1], self.frames[i]
            if (a.value == b.value and len(self.frames) > i + 1 and b.value == self.frames[i + 1].value
                    and (not is_looped or a.tick >= ret or b.tick < ret)):
                del self.frames[i]
                i -= 1
            i += 1


AXES = ("x", "y", "z", "pitch", "yaw", "roll", "scaleX", "scaleY", "scaleZ", "bend", "axis")


class StateCollection:
    def __init__(self, x=0.0, y=0.0, z=0.0, pitch=0.0, yaw=0.0, roll=0.0, threshold=8.0,
                 bendable=True, scalable=True):
        self.x = State("x", x, threshold, False)
        self.y = State("y", y, threshold, False)
        self.z = State("z", z, threshold, False)
        self.pitch = State("pitch", pitch, 0.0, True)
        self.yaw = State("yaw", yaw, 0.0, True)
        self.roll = State("roll", roll, 0.0, True)
        self.bendable = bendable
        self.bend = State("bend", 0.0, 0.0, True) if bendable else None
        self.axis = State("axis", 0.0, 0.0, True) if bendable else None
        self.scalable = scalable
        self.scaleX = State("scaleX", 1.0, 0.0, False) if scalable else None
        self.scaleY = State("scaleY", 1.0, 0.0, False) if scalable else None
        self.scaleZ = State("scaleZ", 1.0, 0.0, False) if scalable else None

    def states(self):
        return [s for s in (self.x, self.y, self.z, self.pitch, self.yaw, self.roll, self.bend, self.axis,
                            self.scaleX, self.scaleY, self.scaleZ) if s is not None]

    def vec(self, kind):
        if kind == POSITION:
            return (self.x, self.y, self.z)
        if kind == ROTATION:
            return (self.pitch, self.yaw, self.roll)
        if kind == SCALE:
            return (self.scaleX, self.scaleY, self.scaleZ)
        if kind == BEND:
            return (self.bend, self.axis)
        raise KeyError(kind)


def _builder_parts(threshold=8.0) -> dict[str, StateCollection]:
    """AnimationBuilder's nine parts and their defaults (pixels / radians)."""
    return {
        "head": StateCollection(0, 0, 0, threshold=threshold, bendable=False),
        "body": StateCollection(0, 0, 0, threshold=threshold / 8.0, bendable=True),
        "rightArm": StateCollection(-5.0, 2.0, 0.0, threshold=threshold),
        "leftArm": StateCollection(5.0, 2.0, 0.0, threshold=threshold),
        "leftLeg": StateCollection(1.9, 12.0, 0.1, threshold=threshold),
        "rightLeg": StateCollection(-1.9, 12.0, 0.1, threshold=threshold),
        "leftItem": StateCollection(0, 0, 0, threshold=threshold, bendable=False),
        "rightItem": StateCollection(0, 0, 0, threshold=threshold, bendable=False),
        "torso": StateCollection(0, 0, 0, threshold=threshold),
    }


@dataclass
class Animation:
    name: str
    begin: int
    end: int
    stop: int
    infinite: bool
    ret: int
    parts: dict
    easing_before: bool
    fmt: str
    extra: dict = field(default_factory=dict)

    @property
    def length(self):  # KeyframeAnimation.getLength() returns stopTick
        return self.stop


def _build(name, begin, end, stop, looped, ret, parts, easing_before, fmt, extra) -> Animation:
    begin = max(begin, 0)
    end = max(begin + 1, end)
    stop = end + 3 if stop <= end else stop
    if looped and not (0 <= ret <= end):
        raise ValueError("Trying to construct invalid animation")
    for pc in parts.values():
        for s in pc.states():
            for kf in s.frames:
                if kf.tick < 0 or not math.isfinite(kf.value):
                    raise ValueError(f"Animation is invalid: {kf}")
    return Animation(name, begin, end, stop, looped, ret, parts, easing_before, fmt, extra)


def _as_bool(v) -> bool:
    if isinstance(v, bool):
        return v
    if isinstance(v, str):
        return v.lower() == "true"  # JsonPrimitive.getAsBoolean on a string is Boolean.parseBoolean
    raise ValueError(f"not a boolean: {v!r}")


# ----------------------------------------------------------------------------- the emotecraft reader (AnimationJson)


def load_emotecraft(obj: dict) -> list[Animation]:
    version = int(obj.get("version", 1))
    e = obj["emote"]
    begin = int(e.get("beginTick", 0))
    end = int(e["endTick"])
    if end <= 0:
        raise ValueError("endTick must be bigger than 0")
    looped, ret = False, 0
    if "isLoop" in e and "returnTick" in e:
        looped = _as_bool(e["isLoop"])
        ret = int(e["returnTick"])
        if looped and (ret > end or ret < 0):
            raise ValueError("return tick have to be smaller than endTick and not smaller than 0")
    stop = int(e["stopTick"]) if "stopTick" in e else end
    degrees = _as_bool(e["degrees"]) if "degrees" in e else True
    easing_before = _as_bool(e["easeBeforeKeyframe"]) if "easeBeforeKeyframe" in e else False
    parts = _builder_parts()
    for m in e["moves"]:
        tick = int(m["tick"])
        easing = m.get("easing", "linear")
        turn = int(m.get("turn", 0))
        for key, node in m.items():
            if key in ("tick", "comment", "easing", "turn"):
                continue
            # AnimationJson does not skip "easingArg": it is read as a body part and a number there
            # makes getAsJsonObject() throw, which drops the whole file. Same here.
            if not isinstance(node, dict):
                raise ValueError(f"move key {key!r} is not an object: the library would reject this file")
            name = "body" if (version < 3 and key == "torso") else key
            if name not in parts:
                parts[name] = StateCollection()
            pc = parts[name]
            for ax in AXES:
                if ax in node:
                    st = getattr(pc, ax)
                    if st is None:
                        raise ValueError(f"{name}.{ax}: part has no such channel (library NPE)")
                    st.add_keyframe(tick, float(node[ax]), ease_from_string(easing), turn, degrees)
    name = str(obj["name"])
    extra = {k: v for k, v in obj.items() if k not in ("uuid", "comment", "version", "emote")
             and not isinstance(v, (dict, list))}
    if version > 3:
        raise ValueError("PlayerAnimator library can only process version 3")
    for pc in parts.values():
        for s in pc.states():
            s.optimize(looped, ret)
    return [_build(name.lower(), begin, end, stop, looped, ret, parts, easing_before, "emotecraft", extra)]


# ----------------------------------------------------------------------------- the GeckoLib reader (GeckoLibSerializer)


def _snake2camel(s: str) -> str:
    out, up = [], False
    for c in s:
        if c == "_":
            up = True
        else:
            out.append(c.upper() if up else c)
            up = False
    return "".join(out)


def _gecko_tick(key: str) -> int:
    return int(F32(float(key)) * F32(20.0))  # (int)(Float.parseFloat(key) * 20.0F): truncation


def load_gecko(obj: dict) -> list[Animation]:
    out = []
    for name, node in obj["animations"].items():
        parts = _builder_parts()
        looped, ret, stop, begin = False, 0, 0, 0
        if "animation_length" in node:
            end = math.ceil(F32(node["animation_length"]) * F32(20.0))
            if "loop" in node:
                lp = node["loop"]
                looped = isinstance(lp, bool) and lp
                if not looped and isinstance(lp, str) and lp == "hold_on_last_frame":
                    looped, ret = True, end
                else:
                    end -= 1
            _gecko_bones(parts, node["bones"])
        elif "loop" in node and node["loop"] is True:
            end = stop = 1
            looped, ret = True, 0
            _gecko_bones(parts, node["bones"])
        else:
            end = 0
        out.append(_build(name.lower(), begin, end, stop, looped, ret, parts, False, "gecko", {}))
    return out


def _gecko_bones(parts, bones):
    for key, node in bones.items():
        if key.endswith("_bend"):
            raise ValueError("bend channels need bendylib, which this project does not ship")
        pname = _snake2camel(key)
        if pname not in parts:
            parts[pname] = StateCollection()
        pc = parts[pname]
        for kind, jkey in ((ROTATION, "rotation"), (POSITION, "position"), (SCALE, "scale")):
            if jkey not in node:
                continue
            jv = node[jkey]
            if isinstance(jv, list):
                _gecko_vec(parts, pc, kind, 0, "LINEAR", jv, None)
                continue
            for tkey, val in jv.items():
                if tkey == "vector":
                    _gecko_vec(parts, pc, kind, 0, "LINEAR", val, None)
                elif tkey != "easing":
                    tick = _gecko_tick(tkey)
                    if isinstance(val, list):
                        # a bare array keyframe: CONSTANT for rotation, LINEAR for position and scale
                        _gecko_vec(parts, pc, kind, tick, "CONSTANT" if kind == ROTATION else "LINEAR", val, None)
                    else:
                        ease = "LINEAR"
                        arg = None
                        if "lerp_mode" in val:
                            ease = ease_from_string(val["lerp_mode"])
                        if "easing" in val:
                            ease = ease_from_string(val["easing"])
                        if "easingArgs" in val:
                            arg = float(val["easingArgs"][0])
                        for sub in ("pre", "vector", "post"):
                            if sub in val:
                                v = val[sub]
                                v = v if isinstance(v, list) else v["vector"]
                                _gecko_vec(parts, pc, kind, tick, ease, v, arg)


def _gecko_vec(parts, pc, kind, tick, ease, arr, arg):
    states = pc.vec(kind)
    is_body = pc is parts["body"]
    for i in range(3):
        v = float(arr[i])
        if kind == POSITION:
            if is_body:
                v /= 16.0
                if i == 0:
                    v = -v
            elif i == 1:
                v = -v
        elif kind == ROTATION and is_body and i != 2:
            v = -v
        if kind != SCALE:
            v += states[i].default
        states[i].add_keyframe(tick, v, ease, 0, True, arg)


def load(path) -> list[Animation]:
    """AnimationCodecs for a .json in player_animations: emotecraft first, which itself hands any
    file without an "emote" key to the GeckoLib reader."""
    with open(path, "r", encoding="utf-8") as fh:
        obj = json.load(fh)
    if "emote" in obj:
        return load_emotecraft(obj)
    return load_gecko(obj)


# ----------------------------------------------------------------------------- playback (KeyframeAnimationPlayer)


class Player:
    """KeyframeAnimationPlayer. get(part, kind, value0) is get3DTransform."""

    def __init__(self, anim: Animation, t: int = 0):
        self.data = anim
        self.running = True
        self.tick_now = t
        self.loop_started = False
        self.delta = 0.0
        if anim.infinite and t > anim.ret:
            self.tick_now = (t - anim.ret) % (anim.end - anim.ret + 1) + anim.ret

    def tick(self):
        if self.running:
            self.tick_now += 1
            if self.data.infinite and self.tick_now > self.data.end:
                self.tick_now = self.data.ret
                self.loop_started = True
            if self.tick_now >= self.data.stop:
                self.running = False

    def active(self):
        return self.running

    def setup(self, delta: float):
        self.delta = delta

    # --- Axis
    def _find_before(self, st: State, pos: int, current: float) -> KeyFrame:
        d = self.data
        if pos == -1:
            if self.tick_now < d.begin:
                return KeyFrame(0, current)
            if self.tick_now < d.end:
                return KeyFrame(d.begin, st.default)
            return KeyFrame(d.end, st.default)
        f = st.frames[pos]
        if (not d.infinite and self.tick_now >= d.end and pos == len(st.frames) - 1 and f.tick < d.end):
            return KeyFrame(d.end, f.value, f.ease, f.arg)
        return f

    def _find_after(self, st: State, pos: int, current: float) -> KeyFrame:
        d = self.data
        if len(st.frames) > pos + 1:
            return st.frames[pos + 1]
        if d.infinite:
            return KeyFrame(d.end + 1, st.default)
        if self.tick_now < d.end and len(st.frames) > 0:
            last = st.frames[-1]
            return KeyFrame(d.end, last.value, last.ease, last.arg)
        if self.tick_now >= d.end:
            return KeyFrame(d.stop, current)
        if self.tick_now >= d.begin:
            return KeyFrame(d.end, st.default)
        return KeyFrame(d.begin, st.default)

    def axis_value(self, st: Optional[State], current: float) -> float:
        if st is None or not st.enabled:
            return current
        d = self.data
        pos = st.find_at_tick(self.tick_now)
        before = self._find_before(st, pos, current)
        if self.loop_started and before.tick < d.ret:
            before = self._find_before(st, st.find_at_tick(d.end), current)
        after = self._find_after(st, pos, current)
        if d.infinite and after.tick > d.end:
            after = self._find_after(st, st.find_at_tick(d.ret - 1), current)
        tb, ta = before.tick, after.tick
        if tb >= ta:
            if self.tick_now < tb:
                tb -= d.end - d.ret + 1
            else:
                ta += d.end - d.ret + 1
        if tb == ta:
            return before.value
        f = (self.tick_now + self.delta - tb) / (ta - tb)
        src = after if d.easing_before else before
        return lerp(ease_invoke(src.ease, f, src.arg), before.value, after.value)

    def get(self, part: str, kind: str, value0):
        pc = self.data.parts.get(part)
        if pc is None:
            return tuple(value0)
        sts = pc.vec(kind)
        if kind in (ROTATION, BEND):  # RotationAxis wraps input and output
            return tuple(clamp_to_radian(self.axis_value(s, clamp_to_radian(v))) for s, v in zip(sts, value0))
        return tuple(self.axis_value(s, v) for s, v in zip(sts, value0))


# ----------------------------------------------------------------------------- modifiers


class Mirror:
    """MirrorModifier: swaps left/right arms, legs and items; negates position x and rotation y, z."""
    MAP = {"leftArm": "rightArm", "leftLeg": "rightLeg", "leftItem": "rightItem",
           "rightArm": "leftArm", "rightLeg": "leftLeg", "rightItem": "leftItem"}

    def __init__(self, inner):
        self.inner = inner

    @staticmethod
    def _t(v, kind):
        if kind == POSITION:
            return (-v[0], v[1], v[2])
        if kind == ROTATION:
            return (v[0], -v[1], -v[2])
        if kind == BEND:
            return (v[0], -v[1])
        return tuple(v)

    def tick(self):
        self.inner.tick()

    def setup(self, d):
        self.inner.setup(d)

    def active(self):
        return self.inner.active()

    def get(self, part, kind, value0):
        part = self.MAP.get(part, part)
        v0 = self._t(value0, kind)
        return self._t(self.inner.get(part, kind, v0), kind)


class Fade:
    """AbstractFadeModifier.standardFadeIn(length, ease) as used by ModifierLayer.replaceAnimationWithFade:
    blends from `begin` (which keeps ticking) to `new`."""

    def __init__(self, begin, new, length: int, ease: str = "INOUTSINE"):
        self.begin, self.new, self.length, self.ease = begin, new, length, ease
        self.time = 0
        self.delta = 0.0

    def tick(self):
        self.new.tick()
        if self.begin is not None:
            self.begin.tick()
        self.time += 1

    def setup(self, d):
        self.delta = d
        self.new.setup(d)
        if self.begin is not None:
            self.begin.setup(d)

    def active(self):
        return self.new.active() or (self.begin is not None and self.begin.active())

    def get(self, part, kind, value0):
        prog = (self.time + self.delta) / self.length
        if prog > 1.0:
            return self.new.get(part, kind, value0)
        a = ease_invoke(self.ease, prog)
        anim = self.new.get(part, kind, value0)
        src = self.begin.get(part, kind, value0) if (self.begin is not None and self.begin.active()) else tuple(value0)
        return tuple(x * a + y * (1.0 - a) for x, y in zip(anim, src))


# ----------------------------------------------------------------------------- writer (our authoring format)


def emotecraft_json(name: str, end: int, moves: list[dict], *, stop: Optional[int] = None, loop: bool = False,
                    ret: int = 0, begin: int = 0, description: str = "") -> dict:
    """Builds a version-3 emotecraft animation in degrees. Each move is
    {"tick": t, "easing": NAME, "<part>": {"<axis>": value}} with exactly one part and one axis."""
    emote = {"beginTick": begin, "endTick": end, "stopTick": stop if stop is not None else end + 3,
             "isLoop": loop, "returnTick": ret, "degrees": True, "moves": moves}
    out = {"name": name, "author": "Cosmic Breach", "description": description, "version": 3, "emote": emote}
    return out
