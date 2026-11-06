#!/usr/bin/env bash
# Publishes the built plugin as GitHub release v<pluginVersion>, together with updatePlugins.xml: a custom plugin
# repository file. Add https://github.com/<owner>/<repo>/releases/latest/download/updatePlugins.xml under
# Settings → Plugins → ⚙ → Manage Plugin Repositories, and IntelliJ offers new versions as plugin updates.
#
# Needs GH_TOKEN and GITHUB_REPOSITORY, and build/distributions/<name>-<version>.zip from ./gradlew buildPlugin.
# Optional: RELEASE_NOTES. Does nothing if the release already exists.
set -euo pipefail

prop() { grep "^$1=" gradle.properties | cut -d= -f2-; }
version=$(prop pluginVersion)
since=$(prop pluginSinceBuild)
until=$(prop pluginUntilBuild)
tag="v$version"

if gh release view "$tag" >/dev/null 2>&1; then
  echo "Release $tag already exists"
  exit 0
fi

zip=$(ls build/distributions/*-"$version".zip)
zip_name=$(basename "$zip")
url="https://github.com/$GITHUB_REPOSITORY/releases/download/$tag/$zip_name"

cat > build/updatePlugins.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="dev.njk.nunjucks" url="$url" version="$version">
    <idea-version since-build="$since" until-build="$until"/>
    <name>Nunjucks Support</name>
    <vendor>njk</vendor>
    <description>Nunjucks templates: highlighting, completion, navigation and TypeScript integration.</description>
  </plugin>
</plugins>
EOF

notes=${RELEASE_NOTES:-"Nunjucks Support $version for IntelliJ builds $since to $until."}
notes="$notes

Install: Settings → Plugins → ⚙ → Install Plugin from Disk, and pick $zip_name.
Automatic updates: add https://github.com/$GITHUB_REPOSITORY/releases/latest/download/updatePlugins.xml under Settings → Plugins → ⚙ → Manage Plugin Repositories."

gh release create "$tag" "$zip" build/updatePlugins.xml --title "Nunjucks Support $version" --notes "$notes"
