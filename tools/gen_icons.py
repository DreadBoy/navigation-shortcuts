#!/usr/bin/env python3
"""Generate the own Sticker-style icon set (app/src/main/res/drawable/ic_own_*.xml).

Each glyph is defined once below on a 24x24 grid (drawn within ~3..21) as:
  sil    - silhouette: the filled body of the glyph (closed subpaths)
  holes  - optional detail regions cut out of the full-opacity fill (door, cross, P, ...);
           they show the paler silhouette fill underneath
  fg     - optional explicit full-opacity fill path (overrides sil + holes)
  line   - the dark outline (silhouette edge + interior details)

The Sticker recipe (docs/decisions.md 1.6, docs/icons.md) is applied to every glyph:
group scaled 0.7 around the centre, containing bottom to top
  1. white die-cut sticker: sil + line, filled white and stroked white 6 units, round joins
  2. silhouette fill with the coral gradient at ~63 % alpha
  3. detail ("fg") fill with the coral gradient at 100 %
  4. outline, 1.5 units, round caps/joins, @color/icon_sticker_outline

Python stdlib only. Usage: python3 tools/gen_icons.py [--out DIR]
"""
import argparse
import math
import os

# --------------------------------------------------------------------------- helpers


def f(v):
    """Format a number compactly (max 2 decimals, no trailing zeros)."""
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def pt(p):
    return f"{f(p[0])},{f(p[1])}"


def rpoly(points, r=1.0):
    """Closed polygon with corners softened by quadratic curves (slightly organic look).

    r may be a number or a list of per-vertex radii."""
    n = len(points)
    radii = r if isinstance(r, (list, tuple)) else [r] * n
    out = []
    for i, p in enumerate(points):
        a, b = points[i - 1], points[(i + 1) % n]
        ri = radii[i]
        if ri <= 0:
            out.append(("L", p))
            continue

        def toward(q, rr):
            dx, dy = q[0] - p[0], q[1] - p[1]
            d = math.hypot(dx, dy)
            rr = min(rr, d / 2)
            return (p[0] + dx * rr / d, p[1] + dy * rr / d)

        out.append(("C", toward(a, ri), p, toward(b, ri)))
    s = []
    first = out[0]
    start = first[1] if first[0] == "L" else first[3]
    s.append(f"M{pt(start)}")
    for item in out[1:] + [out[0]]:
        if item[0] == "L":
            s.append(f"L{pt(item[1])}")
        else:
            s.append(f"L{pt(item[1])}Q{pt(item[2])} {pt(item[3])}")
    return "".join(s) + "Z"


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    if r <= 0:
        return f"M{f(x)},{f(y)}H{f(x + w)}V{f(y + h)}H{f(x)}Z"
    return (f"M{f(x + r)},{f(y)}H{f(x + w - r)}A{f(r)},{f(r)} 0 0 1 {f(x + w)},{f(y + r)}"
            f"V{f(y + h - r)}A{f(r)},{f(r)} 0 0 1 {f(x + w - r)},{f(y + h)}"
            f"H{f(x + r)}A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y + h - r)}"
            f"V{f(y + r)}A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)}Z")


def circle(cx, cy, r):
    return (f"M{f(cx - r)},{f(cy)}A{f(r)},{f(r)} 0 1 1 {f(cx + r)},{f(cy)}"
            f"A{f(r)},{f(r)} 0 1 1 {f(cx - r)},{f(cy)}Z")


def rotate(points, deg, c=(12, 12)):
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    return [(c[0] + (x - c[0]) * ca - (y - c[1]) * sa,
             c[1] + (x - c[0]) * sa + (y - c[1]) * ca) for x, y in points]


def heart(cx, cy, s):
    """Heart centred on (cx, cy), roughly 2*s wide."""
    return (f"M{f(cx)},{f(cy + 1.45 * s)}"
            f"C{f(cx - 0.55 * s)},{f(cy + 1.0 * s)} {f(cx - 1.1 * s)},{f(cy + 0.45 * s)} {f(cx - 1.1 * s)},{f(cy - 0.25 * s)}"
            f"C{f(cx - 1.1 * s)},{f(cy - 0.85 * s)} {f(cx - 0.65 * s)},{f(cy - 1.2 * s)} {f(cx - 0.5 * s)},{f(cy - 1.2 * s)}"
            f"C{f(cx - 0.05 * s)},{f(cy - 1.2 * s)} {f(cx)},{f(cy - 0.8 * s)} {f(cx)},{f(cy - 0.6 * s)}"
            f"C{f(cx)},{f(cy - 0.8 * s)} {f(cx + 0.05 * s)},{f(cy - 1.2 * s)} {f(cx + 0.5 * s)},{f(cy - 1.2 * s)}"
            f"C{f(cx + 0.65 * s)},{f(cy - 1.2 * s)} {f(cx + 1.1 * s)},{f(cy - 0.85 * s)} {f(cx + 1.1 * s)},{f(cy - 0.25 * s)}"
            f"C{f(cx + 1.1 * s)},{f(cy + 0.45 * s)} {f(cx + 0.55 * s)},{f(cy + 1.0 * s)} {f(cx)},{f(cy + 1.45 * s)}Z")


# --------------------------------------------------------------------------- glyphs

GLYPHS = {}


def glyph(name, sil, line, holes=(), fg=None, title=""):
    GLYPHS[name] = dict(sil=sil, line=line, holes=list(holes), fg=fg, title=title)


def _home():
    house = rpoly([(12, 3.4), (15.4, 6.3), (15.4, 4.6), (18.2, 4.6), (18.2, 8.6), (21, 11),
                   (18.9, 11), (18.9, 20.4), (5.1, 20.4), (5.1, 11), (3, 11)],
                  [1.2, 0.3, 0.6, 0.6, 0.3, 1, 0.4, 1.4, 1.4, 0.4, 1])
    door = "M9.8,20.4V15.6A2.2,2.2 0 0 1 14.2,15.6V20.4Z"
    glyph("home", house, house + door.replace("Z", ""), holes=[door], title="Home")


def _work():
    body = rrect(3, 7.6, 18, 12.6, 2.4)
    handle_outer = rrect(8.4, 3.8, 7.2, 5, 1.8)
    handle_inner = "M10.2,7.6V6.4A0.8,0.8 0 0 1 11,5.6H13A0.8,0.8 0 0 1 13.8,6.4V7.6"
    strap = "M3,12.6H10.4M13.6,12.6H21"
    clasp = rrect(10.4, 11.2, 3.2, 3, 0.9)
    glyph("work", body + handle_outer, body + "M8.4,7.6V6.1A2.3,2.3 0 0 1 10.7,3.8H13.3A2.3,2.3 0 0 1 15.6,6.1V7.6"
          + handle_inner + strap + clasp, holes=[clasp], fg=body + clasp, title="Work")


def _gym():
    parts = [rrect(3, 9, 2.6, 6, 1), rrect(5.6, 6.2, 3.2, 11.6, 1.4), rrect(8.8, 10.7, 6.4, 2.6, 0.4),
             rrect(15.2, 6.2, 3.2, 11.6, 1.4), rrect(18.4, 9, 2.6, 6, 1)]
    sil = "".join(parts)
    glyph("gym", sil, sil, title="Gym")


def _stadium():
    # oval arena seen at an angle: rim, rectangular pitch, front wall with gates, floodlights
    bowl = "M3,12A9,3.6 0 0 1 21,12V15.4A9,3.6 0 0 1 3,15.4Z"
    rim = "M3,12A9,3.6 0 0 0 21,12"
    pitch = rpoly([(8, 10.5), (16, 10.5), (16.9, 13.4), (7.1, 13.4)], 0.4)
    halfway = "M12,10.5V13.4" + circle(12, 11.95, 0.9)
    gates = "M7.4,18.1V17.3A0.9,0.9 0 0 1 9.2,17.3V18.4M14.8,18.4V17.3A0.9,0.9 0 0 1 16.6,17.3V18.1"
    lights = rpoly([(3.2, 4.2), (7.2, 3.4), (7.2, 5.6), (3.2, 6.4)], 0.6) + \
        rpoly([(20.8, 4.2), (16.8, 3.4), (16.8, 5.6), (20.8, 6.4)], 0.6)
    masts = "M5.2,6V9.6M18.8,6V9.6"
    glyph("stadium", bowl + lights, bowl + rim + pitch + halfway + gates + masts + lights,
          holes=[pitch], title="Stadium")


def _school():
    board = rpoly([(12, 4.2), (21, 8.8), (12, 13.4), (3, 8.8)], [1, 1, 1, 1])
    base = "M6.6,11.4V15.6C6.6,17.4 9.2,18.8 12,18.8C14.8,18.8 17.4,17.4 17.4,15.6V11.4L12,14.2Z"
    tassel = "M12,8.8L19.4,10.4V16"
    tassel_end = rrect(18.4, 15.6, 2, 3.4, 0.8)
    glyph("school", board + base + tassel_end, base + board + tassel + tassel_end,
          title="School")


def _pool():
    water = ("M3,14.4Q4.5,13 6,14.4T9,14.4T12,14.4T15,14.4T18,14.4T21,14.4"
             "V18.4A2,2 0 0 1 19,20.4H5A2,2 0 0 1 3,18.4Z")
    wave = "M6,17.6Q7.5,16.4 9,17.6T12,17.6T15,17.6T18,17.6"
    ladder = ("M8.4,13.6V6A2.4,2.4 0 0 0 3.6,6V6.8M15.6,13.6V6A2.4,2.4 0 0 0 10.8,6V6.8"
              "M8.4,8.4H15.6M8.4,11.4H15.6")
    rails = rrect(8.4, 7, 7.2, 6, 0)
    glyph("pool", water + rails, water + wave + ladder, fg=water, title="Pool")


def _shop():
    bag = rpoly([(5, 8.2), (19, 8.2), (20.4, 20.4), (3.6, 20.4)], [1.2, 1.2, 1.6, 1.6])
    handle = "M8.6,10.6V7.2A3.4,3.4 0 0 1 15.4,7.2V10.6"
    glyph("shop", bag, bag + handle + circle(8.6, 11.2, 0.6) + circle(15.4, 11.2, 0.6), title="Shop")


def _restaurant():
    fork = ("M4.4,3.6V8.2A2.6,2.6 0 0 0 6.2,10.7V19.5A0.9,0.9 0 0 0 8,19.5V10.7"
            "A2.6,2.6 0 0 0 9.8,8.2V3.6Z")
    tines = "M6.2,3.6V7.6M8,3.6V7.6"
    knife = "M18.6,3.6C16,4.6 14.4,7.8 14.4,12.2A1,1 0 0 0 15.4,13.2H16.8V19.5A0.9,0.9 0 0 0 18.6,19.5Z"
    glyph("restaurant", fork + knife, fork + tines + knife, title="Restaurant")


def _pin():
    pin = "M12,21.2C9,18.6 5,14.6 5,10.2A7,7 0 0 1 19,10.2C19,14.6 15,18.6 12,21.2Z"
    hole = circle(12, 10.2, 2.6)
    glyph("pin", pin, pin + hole, holes=[hole], title="Pin")


def _hospital():
    sq = rrect(3.4, 3.4, 17.2, 17.2, 4.4)
    cross = rpoly([(10, 6.4), (14, 6.4), (14, 10), (17.6, 10), (17.6, 14), (14, 14), (14, 17.6),
                   (10, 17.6), (10, 14), (6.4, 14), (6.4, 10), (10, 10)],
                  [0.8, 0.8, 0.3, 0.8, 0.8, 0.3, 0.8, 0.8, 0.3, 0.8, 0.8, 0.3])
    glyph("hospital", sq, sq + cross, holes=[cross], title="Hospital")


def _airport():
    plane = [(10.9, 4.2), (13.1, 4.2), (13.1, 9.2), (20.6, 13.4), (20.6, 15.2), (13.1, 13.4),
             (13.1, 17.4), (15.8, 19.2), (15.8, 20.6), (12, 19.8), (8.2, 20.6), (8.2, 19.2),
             (10.9, 17.4), (10.9, 13.4), (3.4, 15.2), (3.4, 13.4), (10.9, 9.2)]
    # point to the top right, then re-centre the rotated outline
    plane = [(x + 1.05, y - 1.45) for x, y in rotate(plane, 45, (12, 12.4))]
    sil = rpoly(plane, [1.1, 1.1, 0.4, 0.6, 0.6, 0.4, 0.4, 0.5, 0.5, 0.4, 0.5, 0.5, 0.4, 0.4, 0.6, 0.6, 0.4])
    glyph("airport", sil, sil, title="Airport")


def _parking():
    sq = rrect(3.4, 3.4, 17.2, 17.2, 4.4)
    p_outer = "M9.4,18.2A0.8,0.8 0 0 1 8.6,17.4V7A0.8,0.8 0 0 1 9.4,6.2H13.4A4,4 0 0 1 13.4,14.2H11.6V17.4A0.8,0.8 0 0 1 10.8,18.2Z"
    bowl = "M11.6,8.8V11.6H13.2A1.4,1.4 0 0 0 13.2,8.8Z"
    glyph("parking", sq, sq + p_outer + bowl, holes=[p_outer, bowl], title="Parking")


def _cafe():
    cup = "M4.6,9.4H17.4V13.8A5,5 0 0 1 12.4,18.8H9.6A5,5 0 0 1 4.6,13.8Z"
    handle = "M17.4,10.8H18.4A2.5,2.5 0 0 1 18.4,15.8H16.8"
    handle_fill = "M17.4,10.8H18.4A2.5,2.5 0 0 1 18.4,15.8H16.8Z"
    saucer = "M4,20.8H18"
    steam = "M8.4,3.6C7.4,4.8 9.4,5.8 8.4,7.4M12,3.6C11,4.8 13,5.8 12,7.4M15.6,3.6C14.6,4.8 16.6,5.8 15.6,7.4"
    glyph("cafe", cup + handle_fill, cup + handle + saucer + steam, fg=cup, title="Cafe")


def _family():
    house = rpoly([(12, 3.4), (21, 11), (18.9, 11), (18.9, 20.4), (5.1, 20.4), (5.1, 11), (3, 11)],
                  [1.2, 1, 0.4, 1.4, 1.4, 0.4, 1])
    h = heart(12, 15.2, 3)
    glyph("family", house, house + h, holes=[h], title="Family")


def _mountain():
    pts = [(3, 19.6), (9.4, 6.4), (12.6, 12.2), (15.2, 8.8), (21, 19.6)]
    sil = rpoly(pts, [0.8, 1.2, 0.8, 1, 0.8])
    snow = "M7.1,11.1L8.5,12.3L9.8,11L11.1,12.2L11.6,11.4"
    sun = circle(17.6, 5.2, 2.2)
    body = "M3.6,19.6L7.1,11.1L8.5,12.3L9.8,11L11.1,12.2L11.6,11.4L12.6,12.2L14.6,9.6Q15.2,8.8 15.8,9.6L20.4,19.6Z"
    glyph("mountain", sil + sun, sil + snow + sun, fg=body + sun, title="Hiking")


for _fn in (_home, _work, _gym, _stadium, _school, _pool, _shop, _restaurant, _pin, _hospital,
            _airport, _parking, _cafe, _family, _mountain):
    _fn()

# --------------------------------------------------------------------------- output

GRADIENT = """            <aapt:attr name="android:fillColor">
                <gradient
                    android:type="linear"
                    android:startX="7"
                    android:startY="3"
                    android:endX="17"
                    android:endY="21">
                    <item android:offset="0" android:color="@color/icon_sticker_grad_1" />
                    <item android:offset="0.35" android:color="@color/icon_sticker_grad_2" />
                    <item android:offset="0.6" android:color="@color/icon_sticker_grad_3" />
                    <item android:offset="1" android:color="@color/icon_sticker_grad_4" />
                </gradient>
            </aapt:attr>"""


def subpaths(d):
    return ["M" + part for part in d.split("M") if part]


def sticker_path(g):
    """Outline subpaths plus any silhouette subpaths the outline does not already contain."""
    extra = [sp for sp in subpaths(g["sil"]) if sp not in g["line"]]
    return g["line"] + "".join(extra)


def render(name, g):
    fg = g["fg"] if g["fg"] is not None else g["sil"] + "".join(g["holes"])
    fg_type = "evenOdd" if g["holes"] else "nonZero"
    title = g["title"] or name
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Own Sticker-style icon "{title}" (generated by tools/gen_icons.py; see docs/icons.md).
     UnusedResources is ignored because icons are part of a picker set and may be resolved by name. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="UnusedResources"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <group
        android:pivotX="12"
        android:pivotY="12"
        android:scaleX="0.7"
        android:scaleY="0.7">
        <path
            android:name="sticker"
            android:fillColor="@color/icon_sticker_background"
            android:pathData="{sticker_path(g)}"
            android:strokeColor="@color/icon_sticker_background"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:strokeWidth="6" />
        <path
            android:name="bg"
            android:fillAlpha="0.63"
            android:pathData="{g['sil']}">
{GRADIENT}
        </path>
        <path
            android:name="fg"
            android:fillType="{fg_type}"
            android:pathData="{fg}">
{GRADIENT}
        </path>
        <path
            android:name="outline"
            android:pathData="{g['line']}"
            android:strokeColor="@color/icon_sticker_outline"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:strokeWidth="1.5" />
    </group>
</vector>
"""


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(here, "..", "app", "src", "main", "res", "drawable"))
    args = ap.parse_args()
    for name, g in GLYPHS.items():
        path = os.path.join(args.out, f"ic_own_{name}.xml")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(render(name, g))
        longest = max(len(sticker_path(g)), len(g["line"]), len(g["sil"]))
        print(f"{path}  (longest pathData {longest} chars)")


if __name__ == "__main__":
    main()
