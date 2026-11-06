#!/usr/bin/env python3
"""
Turns a failed build into GitHub annotations and a job summary, so the reason is visible on the run page and
through the public API without downloading logs: failed tests, Plugin Verifier problems, and Gradle's
"What went wrong" section from build/gradle.log.
"""
import glob
import os
import re
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 10


def escape(text):
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def annotate(title, message):
    print(f"::error title={escape(title)}::{escape(message[:3000])}")


def failed_tests():
    for path in glob.glob("build/test-results/test/*.xml"):
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            for problem in case.findall("failure") + case.findall("error"):
                detail = (problem.get("message") or "") + "\n" + (problem.text or "")
                yield f"{case.get('classname')}.{case.get('name')}", detail.strip()


def verifier_problems():
    for path in glob.glob("build/reports/pluginVerifier/**/*.txt", recursive=True):
        name = os.path.basename(path)
        if "problem" in name or "error" in name or "invalid" in name:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read().strip()
            if text:
                yield os.path.relpath(path, "build/reports/pluginVerifier"), text


def gradle_failure():
    try:
        with open("build/gradle.log", encoding="utf-8", errors="replace") as f:
            log = f.read()
    except FileNotFoundError:
        return None
    match = re.search(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", log, re.S)
    if match:
        return match.group(1).strip()
    return "\n".join(log.strip().splitlines()[-40:])


def main():
    summary = ["## Why the build failed", ""]
    count = 0
    for title, detail in failed_tests():
        if count < MAX_ANNOTATIONS:
            annotate(f"Test failed: {title}", detail)
        count += 1
        summary += [f"### Test failed: `{title}`", "```", detail[:4000], "```"]
    for title, detail in verifier_problems():
        if count < MAX_ANNOTATIONS:
            annotate(f"Plugin Verifier: {title}", detail)
        count += 1
        summary += [f"### Plugin Verifier: `{title}`", "```", detail[:4000], "```"]
    gradle = gradle_failure()
    if gradle:
        annotate("Gradle", gradle)
        summary += ["### Gradle", "```", gradle[:6000], "```"]

    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write("\n".join(summary) + "\n")


if __name__ == "__main__":
    main()
