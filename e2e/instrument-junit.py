#!/usr/bin/env python3
"""Runs `adb shell am instrument -w -r ...` and writes its results as a JUnit XML file.

Used by e2e/run-e2e.sh when it tests a given app APK (APP_APK): Gradle's connected test task would re-install the
debug app over it, so the suite is started with `am instrument` directly. The XML has the same shape as Gradle's
(testsuite tests/failures/errors/skipped, testcase name/classname/time, <failure>/<skipped/>), so run-e2e.sh merges
and prints both the same way.

Usage: instrument-junit.py <out.xml> <suite-name> <command...>
Exit:  0 = the instrumentation finished and every test passed or was skipped; 1 = any failure, error or crash.
"""
import subprocess
import sys
import time
from xml.sax.saxutils import escape, quoteattr

out_path, suite_name, cmd = sys.argv[1], sys.argv[2], sys.argv[3:]

# Status codes of AndroidJUnitRunner's raw (-r) output.
START, OK, ERROR, FAILURE, IGNORED, ASSUMPTION = 1, 0, -1, -2, -3, -4

cases = []  # dicts: classname, name, time, kind (ok|failure|error|skipped), message, stack
bundle, key = {}, None
started = {}  # (classname, name) -> start time
result_lines, code = [], None
t0 = time.time()

proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
for raw in proc.stdout:
    sys.stdout.write(raw)
    sys.stdout.flush()
    line = raw.rstrip("\r\n")
    if line.startswith("INSTRUMENTATION_STATUS: "):
        k, _, v = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
        bundle[k], key = v, k
    elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
        status = int(line.split(":", 1)[1].strip())
        tc = (bundle.get("class", ""), bundle.get("test", ""))
        if status == START:
            started[tc] = time.time()
        elif tc[1]:
            begin = started.pop(tc, time.time())
            stack = bundle.get("stack", "")
            kind = {OK: "ok", FAILURE: "failure", ERROR: "error", IGNORED: "skipped", ASSUMPTION: "skipped"}.get(status, "error")
            cases.append(dict(classname=tc[0], name=tc[1], time=time.time() - begin, kind=kind,
                              message=next((l for l in stack.splitlines() if l.strip()), ""), stack=stack))
        bundle, key = {}, None
    elif line.startswith("INSTRUMENTATION_RESULT: "):
        result_lines.append(line[len("INSTRUMENTATION_RESULT: "):])
        key = None
    elif line.startswith("INSTRUMENTATION_CODE: "):
        code = int(line.split(":", 1)[1].strip())
    elif key is not None:
        bundle[key] += "\n" + line  # continuation of a multi-line value (stack traces)
    elif result_lines:
        result_lines[-1] += "\n" + line
rc = proc.wait()

# A crash (e.g. "Process crashed.") ends the run without a status for the running test.
for (cls, name), begin in started.items():
    cases.append(dict(classname=cls, name=name, time=time.time() - begin, kind="error",
                      message="instrumentation ended while the test was running", stack="\n".join(result_lines)))
crashed = code != -1 or rc != 0 or any("shortMsg=" in r for r in result_lines)
if crashed:
    cases.append(dict(classname=suite_name, name="instrumentation", time=0.0, kind="error",
                      message="am instrument did not finish cleanly (exit %s, INSTRUMENTATION_CODE %s)" % (rc, code),
                      stack="\n".join(result_lines)))

count = lambda k: sum(1 for c in cases if c["kind"] == k)
with open(out_path, "w") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n')
    f.write('<testsuite name=%s tests="%d" failures="%d" errors="%d" skipped="%d" time="%.3f">\n' % (
        quoteattr(suite_name), len(cases), count("failure"), count("error"), count("skipped"), time.time() - t0))
    for c in cases:
        f.write('  <testcase name=%s classname=%s time="%.3f"' % (quoteattr(c["name"]), quoteattr(c["classname"]), c["time"]))
        if c["kind"] == "ok":
            f.write("/>\n")
        elif c["kind"] == "skipped":
            f.write("><skipped/></testcase>\n")
        else:
            f.write(">\n    <%s message=%s>%s</%s>\n  </testcase>\n" % (
                c["kind"], quoteattr(c["message"]), escape(c["stack"]), c["kind"]))
    f.write("</testsuite>\n")

sys.exit(0 if not crashed and count("failure") == 0 and count("error") == 0 else 1)
