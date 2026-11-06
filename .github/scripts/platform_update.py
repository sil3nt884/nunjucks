#!/usr/bin/env python3
"""
Points gradle.properties at the newest IntelliJ IDEA release.

- A patch release in a branch the plugin already supports (e.g. 2026.2.5 while until-build is 262.*) only
  changes the IDE the plugin is built and verified against.
- A release in a new branch (e.g. 2026.3, build 263) also widens until-build to that branch and bumps the
  plugin's patch version, so the new build can be told apart from the old one.

The workflow then builds, tests and runs the Plugin Verifier; the change is kept only if all of that passes.

Writes key=value results to $GITHUB_OUTPUT when set. Set NJK_RELEASE_VERSION and NJK_RELEASE_BUILD to try a
release without asking the JetBrains API.
"""
import argparse
import json
import os
import re
import sys
import urllib.request

RELEASES_API = "https://data.services.jetbrains.com/products/releases?code=IIU&latest=true&type=release"


def latest_release():
    version = os.environ.get("NJK_RELEASE_VERSION")
    build = os.environ.get("NJK_RELEASE_BUILD")
    if version and build:
        return version, build
    with urllib.request.urlopen(RELEASES_API, timeout=60) as response:
        release = json.load(response)["IIU"][0]
    return release["version"], release["build"]


def numbers(version):
    return tuple(int(part) for part in re.findall(r"\d+", version))


def read_properties(text):
    props = {}
    for line in text.splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def set_property(text, key, value):
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.MULTILINE)
    if not pattern.search(text):
        sys.exit(f"{key} is missing from gradle.properties")
    return pattern.sub(f"{key}={value}", text, count=1)


def write_outputs(outputs):
    lines = [f"{key}={value}" for key, value in outputs.items()]
    print("\n".join(lines))
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--properties", default="gradle.properties")
    args = parser.parse_args()

    with open(args.properties, encoding="utf-8") as f:
        text = f.read()
    props = read_properties(text)
    version, build = latest_release()
    current = props["platformVersion"]

    if numbers(version) <= numbers(current):
        write_outputs({"updated": "false", "version": version, "build": build, "current": current})
        return

    branch = int(build.split(".")[0])
    until_branch = int(props["pluginUntilBuild"].split(".")[0])
    new_branch = branch > until_branch

    verify = [v.strip() for v in props["verifyIdeVersions"].split(",") if v.strip()]
    oldest = verify[0] if verify else version
    text = set_property(text, "platformVersion", version)
    text = set_property(text, "verifyIdeVersions", ",".join(dict.fromkeys([oldest, version])))

    plugin_version = props["pluginVersion"]
    if new_branch:
        text = set_property(text, "pluginUntilBuild", f"{branch}.*")
        major, minor, patch = (int(n) for n in plugin_version.split("."))
        plugin_version = f"{major}.{minor}.{patch + 1}"
        text = set_property(text, "pluginVersion", plugin_version)

    with open(args.properties, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)

    write_outputs({
        "updated": "true",
        "version": version,
        "build": build,
        "current": current,
        "new_branch": str(new_branch).lower(),
        "until_build": f"{branch}.*" if new_branch else props["pluginUntilBuild"],
        "plugin_version": plugin_version,
    })


if __name__ == "__main__":
    main()
