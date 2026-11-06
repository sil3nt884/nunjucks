# Nunjucks Support for IntelliJ

An IntelliJ Platform plugin for [Nunjucks](https://mozilla.github.io/nunjucks/) templates (`.njk`, `.nunjucks`).

## Features

**Editing**
- Syntax highlighting for tags, expressions, filters, tests, comments and front matter. You can change the colors under *Settings → Editor → Color Scheme → Nunjucks*.
- HTML between tags is a real HTML tree, so HTML/CSS/JS highlighting, completion, Emmet and inspections keep working. You can switch the data language under *Settings → Languages & Frameworks → Template Data Languages*.
- Typing `{{`, `{%` or `{#` adds the closing delimiter. `{%` also opens tag completion.
- *Comment with Block Comment* uses `{# #}`.
- Folding, brace and tag-delimiter matching, and live templates (`nif`, `nfor`, `nblock`, `nmacro`, `nset`, `ninc`, `next`, `nimp`, `nfrom`, …).

**Completion**
- Tags, with the closing tag of the innermost open block (`endif`, `endfor`, …) listed first.
- Filters (built-in, Eleventy and custom `addFilter()` ones), tests after `is`, and globals.
- Variables in scope: `set`, loop variables, macro parameters, imports, `loop` / `caller` / `super`, plus variables that come from outside the template (see below).
- Members: `loop.index` and friends, macros of an `{% import … as forms %}` alias, and keys of dict literals.
- Macro names in `{% from "x" import … %}`, block names from parent templates, and template paths in `extends` / `include` / `import` / `from`.

**Navigation: where does this variable come from?**

Ctrl/Cmd+Click (Go to Declaration) on a variable goes to its local declaration, which follows Nunjucks scoping. If the template doesn't declare it, you get every place it might come from:

| Source | Example |
|---|---|
| A template that includes this one | `{% set user = … %}{% include "card.njk" %}` |
| A child template, for a layout | `{% extends "base.njk" %}{% set pageTitle = "Home" %}` |
| The parent template's top-level `set`s | |
| Front matter | `---\ntitle: Hi\n---` |
| An Eleventy page that uses this file as its layout | `layout: post.njk` in `posts/first.md` |
| `render()` calls in JS/TS | `res.render("profile", { user, posts })` |
| Globals | `env.addGlobal("site", …)`, `app.locals.year = …`, `addGlobalData` |
| Eleventy global data | `_data/site.json` → `site` |

**TypeScript ↔ Nunjucks**

When the bundled *JavaScript and TypeScript* plugin is present (IntelliJ IDEA Ultimate, WebStorm, …), templates understand the TypeScript code that renders them:

- **Typed context.** For `res.render("blog", { posts })` with `posts: Post[]`:
  - `{% for post in posts %}{{ post.author.| }}` completes `Post`/`Author` members.
  - Ctrl+Click goes to the interface property.
  - This also works when the context is a typed variable: `res.render("profile", ctx)` with `ctx: ProfileContext`. Spreads (`{ ...base, user }`) are followed too.
- **Types through templates.** Types flow through `set`, `for`, indexing, calls and filters (`first`, `last`, `sort`, `default`, and custom filters' declared return types).
- **Filters and globals.**
  - `env.addFilter("initials", (a: Author, upper = true): string[] => …)` shows up in completion as `initials(upper?: boolean) string[]`.
  - `env.addGlobal("site", siteConfig)` makes `site.` complete `siteConfig`'s members.
- **Navigation from TypeScript.**
  - The path in `render("…")` / `getTemplate("…")` navigates to the template, completes template paths, and updates when the template is renamed or moved.
  - Gutter icons link `render()` calls to their template, and `addFilter`/`addGlobal`/shortcode registrations to the templates that use them.
- **Find Usages and Rename.** Both work across the boundary. Finding usages of `interface Author { email }`, or of a property in a render context object, includes the templates that use it, and renaming updates them.
- **Search paths from your code.** Template roots come from `nunjucks.configure("views")`, `new FileSystemLoader([...])` and `app.set("views", path.join(__dirname, "views"))` (also `__dirname + "/views"` and `new URL("./views", import.meta.url)`). They are used exactly like Nunjucks uses them. `app.set("view engine", "njk")` sets the extension for `render("home")`.
- **Type annotations** cover values the IDE can't infer, such as macro parameters or untyped JavaScript:

  ```
  {# @type {import("../src/models").Author} author #}
  {# @type posts: Post[] #}
  {# @param {Post} post #}
  {% macro card(post) %}{{ post.title }}{% endmacro %}
  ```

  A name (`Post`) is looked up among project interfaces, classes and type aliases. `import("…")` paths are relative to the template. `T[]` and `Array<T>` are supported.

Without the JavaScript plugin, everything else still works. Context variables then come from scanning the text of `render()` calls, with no types.

**Go to Render Sites**

Right-click a template, in the Project view, the editor or an editor tab, and choose **Go to Render Sites**. It's also under *Navigate*. It lists everywhere the template is rendered from:
- `res.render("…")` / `env.render()` / `getTemplate()` calls
- other string literals in TS/JS naming the template, such as view-name constants
- templates that `include`, `extends` or `import` it
- Eleventy pages that use it as a layout

With a single result it jumps straight there. You can bind a shortcut to it under *Settings → Keymap*.

Navigation also works for:
- Template paths
- Imported macros (`forms.field`, or `field` after `from … import field`)
- Custom filters and shortcodes, which go to their `addFilter()` / `addShortcode()` call
- Blocks, with gutter icons for the blocks they override or that override them
- Dict keys

Find Usages and Rename work for variables, macros and parameters.

### How template paths are resolved

Nunjucks resolves paths against the search paths passed to `nunjucks.configure()`, and the IDE can't know those. So:
- Relative paths (`./x.njk`, `../x.njk`) resolve against the current file.
- Other paths match any project file whose path ends with them.
- When several files match, the plugin uses template roots it infers from the project. A root is inferred from references that match exactly one file, and `_includes` directories always count as roots.

## Building

You need JDK 25. The build uses the JetBrains Runtime bundled with IntelliJ. `gradle.properties` points at a local IntelliJ IDEA 2026.2 install so nothing large has to be downloaded:

```properties
localIdePath=C:/Program Files/JetBrains/IntelliJ IDEA 2026.2
org.gradle.java.installations.paths=C:/Program Files/JetBrains/IntelliJ IDEA 2026.2/jbr
```

Remove `localIdePath` to have Gradle download `platformVersion` instead.

```bash
./gradlew test          # run the tests
./gradlew runIde        # try the plugin in a sandbox IDE
./gradlew buildPlugin   # build/distributions/nunjucks-intellij-<version>.zip
```

To install the zip, go to *Settings → Plugins → ⚙ → Install Plugin from Disk…*.

## Continuous integration and IDE updates

Two GitHub Actions workflows live in `.github/workflows/`:

- **Build** (`build.yml`) runs on every push and pull request: tests, the JetBrains Plugin Verifier, and `buildPlugin`. The plugin zip is attached to the run.
- **Platform update** (`platform-update.yml`) runs every Monday, or by hand from the Actions tab. It asks JetBrains for the newest IntelliJ IDEA release and updates `gradle.properties` with `.github/scripts/platform_update.py`:
  - **Patch release** in a supported branch (e.g. 2026.2.5): the build and verifier target moves to it. The manifest is unchanged.
  - **New branch** (e.g. 2026.3, build 263): `pluginUntilBuild` widens to `263.*` and `pluginVersion` gets a patch bump.

  Then it builds, runs the tests and verifies against the oldest and newest supported IDE (`verifyIdeVersions`):
  - **All green:** the change is committed to `main`. A new branch also gets a GitHub release with the plugin zip.
  - **Anything breaks:** nothing is committed, and an issue titled "IntelliJ IDEA *version* breaks the plugin" links to the run and its reports.

- **Release** (`release.yml`) runs on pushes to `main`. When `pluginVersion` hasn't been released yet, it builds and publishes GitHub release `v<version>` with the plugin zip and `updatePlugins.xml`.

### Getting updates in IntelliJ automatically

Each release includes `updatePlugins.xml`, a custom plugin repository file. To have IntelliJ pick up new versions by itself:

1. Go to *Settings → Plugins → ⚙ → Manage Plugin Repositories…*.
2. Add `https://github.com/sil3nt884/nunjucks/releases/latest/download/updatePlugins.xml`.
3. IntelliJ then offers new versions under *Plugins → Installed* like any Marketplace update. You can also search for "Nunjucks Support" under *Marketplace* to install it from there.

In remote development, add the repository in the remote (backend) IDE's settings, since that's where the plugin does its work.

To release a change yourself, bump `pluginVersion` in `gradle.properties` and push to `main`.

The supported range is the `pluginSinceBuild`/`pluginUntilBuild` pair in `gradle.properties`. Raising `pluginSinceBuild` is left to you. When you do, also update the first entry of `verifyIdeVersions`.

## Layout

| Package | Contents |
|---|---|
| `dev.njk.lexer` | Hand-written lexer and token types |
| `dev.njk.parser` | Recursive descent parser and element types |
| `dev.njk.psi` | PSI classes (tags, statements, declarations, expressions) |
| `dev.njk.template` | View provider that pairs the Nunjucks tree with the HTML tree |
| `dev.njk.highlight` | Lexer highlighting, layered HTML highlighting, annotator, color page |
| `dev.njk.completion` | Completion contributor and built-in tags/filters/tests |
| `dev.njk.resolve` | Scopes, references, template path resolution and external variable sources |
| `dev.njk.index` | File index of template references, `render()` calls and `addFilter`/`addGlobal`/shortcode registrations |
| `dev.njk.editor` | Brace matcher, commenter, folding, typed handler, find usages, gutter icons, live template context |
| `dev.njk.docs` | Quick documentation |
| `dev.njk.types` | Template type model and inference, `@type` annotations, the `dev.njk.typeProvider` extension point |
| `dev.njk.javascript` | TypeScript/JavaScript bridge, loaded via `njk-javascript.xml` only when the JavaScript plugin is installed |

## Not done yet

- Code formatting (Reformat Code doesn't touch Nunjucks tags)
- Scoping for `{% call %}` and `{% filter %}` bodies is approximate
- An untyped JS object built elsewhere (`res.render("x", data)` with `data: any`) can't be traced. Add a type to `data`, or a `{# @type #}` annotation in the template.
- Generic type arguments are not substituted when following interface members (`Page<Post>.items` stays as written)
