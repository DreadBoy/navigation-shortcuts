#!/usr/bin/env python3
"""Convert an Android VectorDrawable XML file to SVG (Python stdlib only).

Supported: <vector> (viewport, width/height, alpha), nested <group> (scaleX/Y, pivotX/Y,
translateX/Y, rotation), <clip-path>, <path> (pathData, fillColor, fillAlpha, strokeColor,
strokeWidth, strokeAlpha, strokeLineCap, strokeLineJoin, strokeMiterLimit, fillType) and
inline <aapt:attr name="android:fillColor|strokeColor"> linear / radial gradients (start/end
/centre colours or <item offset color> stops). Gradient coordinates are in the path's own
coordinate space, like on Android, so they get the enclosing group transforms for free
(gradientUnits="userSpaceOnUse" inside the transformed <g>).

Colours may be literals (#RGB, #ARGB, #RRGGBB, #AARRGGBB) or @color/name references, resolved
from values*/ XML files next to the drawable's res directory (or --colors FILE ...).
Theme attributes (?attr) are not supported and render as black with a warning.

Usage: python3 tools/vd2svg.py input.xml [-o output.svg] [--size PX] [--colors FILE ...]
"""
import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"


def load_colors(files):
    colors = {}
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for c in root.iter("color"):
            if c.get("name") and c.text:
                colors[c.get("name")] = c.text.strip()
    return colors


class Ctx:
    def __init__(self, colors):
        self.colors = colors
        self.defs = []
        self.n = 0

    def new_id(self, prefix):
        self.n += 1
        return f"{prefix}{self.n}"

    def color(self, value, depth=0):
        """Return (svg_colour, opacity) for an Android colour value."""
        if value is None:
            return None, 1.0
        v = value.strip()
        if v.startswith("@color/") or v.startswith("@android:color/"):
            name = v.split("/", 1)[1]
            if v.startswith("@android:color/"):
                builtin = {"transparent": "#00000000", "white": "#FFFFFFFF", "black": "#FF000000"}
                if name in builtin:
                    return self.color(builtin[name])
            if name not in self.colors or depth > 10:
                sys.stderr.write(f"warning: unresolved colour {v}\n")
                return "#000000", 1.0
            return self.color(self.colors[name], depth + 1)
        if v.startswith("?"):
            sys.stderr.write(f"warning: theme attribute {v} not supported\n")
            return "#000000", 1.0
        if not v.startswith("#"):
            return v, 1.0
        h = v[1:]
        if len(h) == 3:
            h = "FF" + "".join(ch * 2 for ch in h)
        elif len(h) == 4:
            h = "".join(ch * 2 for ch in h)
        elif len(h) == 6:
            h = "FF" + h
        a = int(h[0:2], 16) / 255.0
        return "#" + h[2:].upper(), a

    def gradient(self, el):
        """Build an SVG gradient from an Android <gradient> element; return its url()."""
        g = lambda k, d=None: el.get(A + k, d)
        gtype = g("type", "linear")
        gid = self.new_id("g")
        stops = []
        items = el.findall("item")
        if items:
            for it in items:
                c, op = self.color(it.get(A + "color"))
                stops.append((float(it.get(A + "offset", "0")), c, op))
        else:
            c0, o0 = self.color(g("startColor", "#00000000"))
            stops.append((0.0, c0, o0))
            if g("centerColor"):
                c1, o1 = self.color(g("centerColor"))
                stops.append((0.5, c1, o1))
            c2, o2 = self.color(g("endColor", "#00000000"))
            stops.append((1.0, c2, o2))
        spread = {"clamp": "pad", "repeat": "repeat", "mirror": "reflect"}.get(g("tileMode", "clamp"), "pad")
        stop_xml = "".join(
            f'<stop offset="{o}" stop-color="{c}" stop-opacity="{op:.3f}"/>' for o, c, op in stops)
        if gtype == "radial":
            self.defs.append(
                f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" spreadMethod="{spread}" '
                f'cx="{g("centerX", "0")}" cy="{g("centerY", "0")}" r="{g("gradientRadius", "0")}">'
                f"{stop_xml}</radialGradient>")
        else:
            if gtype == "sweep":
                sys.stderr.write("warning: sweep gradient approximated as linear\n")
            self.defs.append(
                f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" spreadMethod="{spread}" '
                f'x1="{g("startX", "0")}" y1="{g("startY", "0")}" x2="{g("endX", "0")}" y2="{g("endY", "0")}">'
                f"{stop_xml}</linearGradient>")
        return f"url(#{gid})"


def esc(s):
    return s.replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;")


def paint(ctx, el, attr):
    """Resolve fill/stroke paint: (paint, opacity) from an attribute or aapt:attr child."""
    for child in el.findall(AAPT + "attr"):
        if child.get("name") == "android:" + attr:
            grad = child.find("gradient")
            if grad is not None:
                return ctx.gradient(grad), 1.0
    val = el.get(A + attr)
    if val is None:
        return "none", 1.0
    c, op = ctx.color(val)
    return c, op


def convert_path(ctx, el):
    d = el.get(A + "pathData", "")
    fill, fop = paint(ctx, el, "fillColor")
    stroke, sop = paint(ctx, el, "strokeColor")
    fop *= float(el.get(A + "fillAlpha", "1"))
    sop *= float(el.get(A + "strokeAlpha", "1"))
    attrs = [f'd="{esc(d)}"', f'fill="{fill}"']
    if fill != "none" and fop < 1:
        attrs.append(f'fill-opacity="{fop:.3f}"')
    if el.get(A + "fillType", "nonZero") == "evenOdd":
        attrs.append('fill-rule="evenodd"')
    sw = float(el.get(A + "strokeWidth", "0"))
    if stroke != "none" and sw > 0:
        attrs.append(f'stroke="{stroke}"')
        attrs.append(f'stroke-width="{sw:g}"')
        if sop < 1:
            attrs.append(f'stroke-opacity="{sop:.3f}"')
        attrs.append(f'stroke-linecap="{el.get(A + "strokeLineCap", "butt")}"')
        attrs.append(f'stroke-linejoin="{el.get(A + "strokeLineJoin", "miter")}"')
        attrs.append(f'stroke-miterlimit="{el.get(A + "strokeMiterLimit", "4")}"')
    return "<path " + " ".join(attrs) + "/>"


def convert_children(ctx, parent):
    out = []
    clip = None
    for el in parent:
        tag = el.tag
        if tag == "group":
            out.append(convert_group(ctx, el))
        elif tag == "path":
            out.append(convert_path(ctx, el))
        elif tag == "clip-path":
            cid = ctx.new_id("c")
            ctx.defs.append(f'<clipPath id="{cid}"><path d="{esc(el.get(A + "pathData", ""))}"/></clipPath>')
            clip = cid
    body = "".join(out)
    if clip:
        # On Android a clip-path applies to the siblings that follow it; approximated for the whole group.
        body = f'<g clip-path="url(#{clip})">{body}</g>'
    return body


def convert_group(ctx, el):
    g = lambda k, d="0": float(el.get(A + k, d))
    px, py = g("pivotX"), g("pivotY")
    tx, ty = g("translateX"), g("translateY")
    sx, sy = g("scaleX", "1"), g("scaleY", "1")
    rot = g("rotation")
    t = (f"translate({tx + px:g} {ty + py:g}) rotate({rot:g}) scale({sx:g} {sy:g}) "
         f"translate({-px:g} {-py:g})")
    return f'<g transform="{t}">{convert_children(ctx, el)}</g>'


def dp(v):
    return float(v.replace("dip", "").replace("dp", "").replace("px", ""))


def convert(path, colors, size=None):
    root = ET.parse(path).getroot()
    if root.tag != "vector":
        raise SystemExit(f"{path}: root element is <{root.tag}>, not <vector>")
    ctx = Ctx(colors)
    vw = float(root.get(A + "viewportWidth"))
    vh = float(root.get(A + "viewportHeight"))
    w = dp(root.get(A + "width", f"{vw}dp"))
    h = dp(root.get(A + "height", f"{vh}dp"))
    if size:
        w, h = size, size * h / w
    body = convert_children(ctx, root)
    alpha = float(root.get(A + "alpha", "1"))
    if alpha < 1:
        body = f'<g opacity="{alpha:.3f}">{body}</g>'
    defs = f"<defs>{''.join(ctx.defs)}</defs>" if ctx.defs else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w:g}" height="{h:g}" '
            f'viewBox="0 0 {vw:g} {vh:g}">{defs}{body}</svg>\n')


def default_color_files(path):
    res = os.path.dirname(os.path.dirname(os.path.abspath(path)))
    return sorted(glob.glob(os.path.join(res, "values", "*.xml")))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("-o", "--output")
    ap.add_argument("--size", type=float, help="output width in px (default: the drawable's dp width)")
    ap.add_argument("--colors", nargs="*", help="colour resource XML files (default: ../values/*.xml)")
    args = ap.parse_args()
    files = args.colors if args.colors else default_color_files(args.input)
    svg = convert(args.input, load_colors(files), args.size)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as fh:
            fh.write(svg)
    else:
        sys.stdout.write(svg)


if __name__ == "__main__":
    main()
