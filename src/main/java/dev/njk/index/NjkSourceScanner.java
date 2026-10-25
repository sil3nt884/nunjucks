package dev.njk.index;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-level scanner shared by the index and by resolution. Finds the places where templates, filters,
 * globals and custom tags are referenced or registered, without needing a JavaScript PSI.
 */
public final class NjkSourceScanner {
    public static final String TEMPLATE = "tpl:";
    public static final String LAYOUT = "layout:";
    public static final String FILTER = "filter:";
    public static final String GLOBAL = "global:";
    public static final String TAG = "tag:";
    public static final String PAIRED_TAG = "ptag:";
    /** A template search path from configure()/FileSystemLoader()/app.set("views"); see {@link #ROOT_DIR}, {@link #ROOT_CWD}. */
    public static final String ROOT = "root:";
    /** Extension from {@code app.set("view engine", "njk")}, used for paths written without one. */
    public static final String VIEW_EXTENSION = "ext:";
    /** Root path relative to the directory of the script ({@code path.join(__dirname, "views")}). */
    public static final String ROOT_DIR = "dir:";
    /** Root path relative to the working directory ({@code configure("views")}). */
    public static final String ROOT_CWD = "cwd:";

    /**
     * @param key    index key, e.g. {@code filter:money}
     * @param name   the referenced name or path as written
     * @param offset offset of {@code name} in the scanned text
     */
    public record Entry(@NotNull String key, @NotNull String name, int offset) {
    }

    public static final Set<String> SCRIPT_EXTENSIONS = Set.of("js", "mjs", "cjs", "ts", "mts", "cts", "jsx", "tsx");
    public static final Set<String> TEMPLATE_EXTENSIONS = Set.of("njk", "nunjucks");
    /** Eleventy content files that can name a layout in their front matter. */
    private static final Set<String> PAGE_EXTENSIONS = Set.of("md", "markdown", "html", "liquid");

    private static final int MAX_LENGTH = 2_000_000;

    private static final Pattern TEMPLATE_REF =
            Pattern.compile("\\{%-?\\s*(?:extends|include|import|from)\\s+([\"'])([^\"'\\r\\n]+)\\1");
    private static final Pattern LAYOUT_REF = Pattern.compile("(?m)^layout:[ \\t]*[\"']?([^\"'\\s#]+)");
    private static final Pattern RENDER_CALL =
            Pattern.compile("\\b(?:render|renderFile|renderAsync)\\s*\\(\\s*([\"'`])([^\"'`\\r\\n]+)\\1");
    private static final Pattern FILTER_DEF =
            Pattern.compile("\\.add(?:Nunjucks)?(?:Async)?Filter\\s*\\(\\s*([\"'`])([\\w$]+)\\1");
    private static final Pattern GLOBAL_DEF = Pattern.compile("\\.add(?:Global|GlobalData)\\s*\\(\\s*([\"'`])([\\w$]+)\\1");
    private static final Pattern LOCALS_DEF = Pattern.compile("\\b(?:app|res|router)\\.locals\\.([\\w$]+)\\s*=[^=]");
    private static final Pattern TAG_DEF =
            Pattern.compile("\\.add(Paired)?(?:Nunjucks)?(?:Async)?(?:Shortcode|Tag)\\s*\\(\\s*([\"'`])([\\w$]+)\\2");
    private static final Pattern EXTENSION_TAGS = Pattern.compile("\\btags\\s*=\\s*\\[([^\\]]*)]");
    private static final Pattern QUOTED_NAME = Pattern.compile("([\"'`])([\\w$]+)\\1");
    private static final Pattern LOADER_CALL =
            Pattern.compile("\\b(?:configure|FileSystemLoader|NodeResolveLoader)\\s*\\(|\\.set\\s*\\(\\s*[\"']views[\"']\\s*,");
    private static final Pattern VIEW_ENGINE = Pattern.compile("\\.set\\s*\\(\\s*[\"']view engine[\"']\\s*,\\s*[\"']\\.?([\\w]+)[\"']");
    private static final Pattern STRING_ARGS = Pattern.compile("[\"'`]([^\"'`$]*)[\"'`]");
    private static final Pattern JOIN_DIRNAME =
            Pattern.compile("(?:path\\.)?(?:join|resolve)\\s*\\(\\s*(__dirname|process\\.cwd\\(\\))((?:\\s*,\\s*[\"'`][^\"'`$]*[\"'`])*)\\s*\\)");
    private static final Pattern URL_META =
            Pattern.compile("(?:fileURLToPath\\s*\\(\\s*)?new\\s+URL\\s*\\(\\s*[\"'`]([^\"'`$]+)[\"'`]\\s*,\\s*import\\.meta\\.url\\s*\\)\\s*\\)?");
    private static final Pattern DIRNAME_CONCAT = Pattern.compile("__dirname\\s*\\+\\s*[\"'`]([^\"'`$]+)[\"'`]");

    private NjkSourceScanner() {
    }

    public static boolean isIndexed(@Nullable String extension) {
        if (extension == null) return false;
        return SCRIPT_EXTENSIONS.contains(extension) || TEMPLATE_EXTENSIONS.contains(extension)
                || PAGE_EXTENSIONS.contains(extension);
    }

    public static boolean isScript(@Nullable String extension) {
        return extension != null && SCRIPT_EXTENSIONS.contains(extension);
    }

    public static List<Entry> scan(@NotNull CharSequence text, @Nullable String extension) {
        List<Entry> result = new ArrayList<>();
        if (extension == null || text.length() > MAX_LENGTH) return result;
        if (SCRIPT_EXTENSIONS.contains(extension)) {
            scanScript(text, result);
        } else {
            if (TEMPLATE_EXTENSIONS.contains(extension)) {
                Matcher m = TEMPLATE_REF.matcher(text);
                while (m.find()) add(result, TEMPLATE + baseName(m.group(2)), m, 2);
            }
            if (TEMPLATE_EXTENSIONS.contains(extension) || PAGE_EXTENSIONS.contains(extension)) {
                int end = frontMatterEnd(text);
                if (end > 0) {
                    Matcher m = LAYOUT_REF.matcher(text).region(0, end);
                    while (m.find()) add(result, LAYOUT + baseName(m.group(1)), m, 1);
                }
            }
        }
        return result;
    }

    private static void scanScript(CharSequence text, List<Entry> result) {
        Matcher m = RENDER_CALL.matcher(text);
        while (m.find()) add(result, TEMPLATE + baseName(m.group(2)), m, 2);
        m = FILTER_DEF.matcher(text);
        while (m.find()) add(result, FILTER + m.group(2), m, 2);
        m = GLOBAL_DEF.matcher(text);
        while (m.find()) add(result, GLOBAL + m.group(2), m, 2);
        m = LOCALS_DEF.matcher(text);
        while (m.find()) add(result, GLOBAL + m.group(1), m, 1);
        m = TAG_DEF.matcher(text);
        while (m.find()) add(result, (m.group(1) != null ? PAIRED_TAG : TAG) + m.group(3), m, 3);
        m = EXTENSION_TAGS.matcher(text);
        while (m.find()) {
            Matcher names = QUOTED_NAME.matcher(text).region(m.start(1), m.end(1));
            while (names.find()) add(result, TAG + names.group(2), names, 2);
        }
        m = VIEW_ENGINE.matcher(text);
        while (m.find()) add(result, VIEW_EXTENSION + m.group(1), m, 1);
        m = LOADER_CALL.matcher(text);
        while (m.find()) scanRoots(text, m.end(), result);
    }

    /** Reads the search path argument of a loader call: a path, or an array of paths. */
    private static void scanRoots(CharSequence text, int start, List<Entry> result) {
        int i = skipWhitespace(text, start);
        if (i < text.length() && text.charAt(i) == '[') {
            i++;
            while (true) {
                i = skipWhitespace(text, i);
                if (i >= text.length() || text.charAt(i) == ']') return;
                int end = scanRoot(text, i, result);
                if (end <= i) return;
                i = skipWhitespace(text, end);
                if (i >= text.length() || text.charAt(i) != ',') return;
                i++;
            }
        }
        scanRoot(text, i, result);
    }

    /** Reads one search path expression at {@code i}; returns the offset after it, or {@code i} if not understood. */
    private static int scanRoot(CharSequence text, int i, List<Entry> result) {
        int limit = Math.min(text.length(), i + 400);
        if (i >= limit) return i;
        char c = text.charAt(i);
        if (c == '"' || c == '\'' || c == '`') {
            Matcher m = STRING_ARGS.matcher(text).region(i, limit);
            if (m.lookingAt()) {
                result.add(new Entry(ROOT + ROOT_CWD + m.group(1), m.group(1), m.start(1)));
                return m.end();
            }
            return i;
        }
        Matcher join = JOIN_DIRNAME.matcher(text).region(i, limit);
        if (join.lookingAt()) {
            StringBuilder path = new StringBuilder();
            Matcher segments = STRING_ARGS.matcher(join.group(2));
            while (segments.find()) {
                if (path.length() > 0) path.append('/');
                path.append(segments.group(1));
            }
            String base = join.group(1).equals("__dirname") ? ROOT_DIR : ROOT_CWD;
            result.add(new Entry(ROOT + base + (path.length() == 0 ? "." : path), path.toString(), join.start()));
            return join.end();
        }
        Matcher url = URL_META.matcher(text).region(i, limit);
        if (url.lookingAt()) {
            result.add(new Entry(ROOT + ROOT_DIR + url.group(1), url.group(1), url.start(1)));
            return url.end();
        }
        Matcher concat = DIRNAME_CONCAT.matcher(text).region(i, limit);
        if (concat.lookingAt()) {
            result.add(new Entry(ROOT + ROOT_DIR + concat.group(1), concat.group(1), concat.start(1)));
            return concat.end();
        }
        return i;
    }

    private static int skipWhitespace(CharSequence text, int i) {
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private static void add(List<Entry> result, String key, Matcher m, int group) {
        result.add(new Entry(key, m.group(group), m.start(group)));
    }

    /** Offset just after the closing {@code ---} of leading front matter, or -1. */
    public static int frontMatterEnd(@NotNull CharSequence text) {
        if (text.length() < 3 || !text.subSequence(0, 3).toString().equals("---")) return -1;
        String s = text.toString();
        int i = 3;
        while (true) {
            int nl = s.indexOf("\n---", i);
            if (nl < 0) return -1;
            int after = nl + 4;
            if (after >= s.length() || s.charAt(after) == '\n' || s.charAt(after) == '\r') return after;
            i = nl + 1;
        }
    }

    /** {@code "layouts/base.njk"} → {@code "base"}: templates are looked up by file name without extension. */
    public static @NotNull String baseName(@NotNull String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = path.substring(slash + 1);
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
