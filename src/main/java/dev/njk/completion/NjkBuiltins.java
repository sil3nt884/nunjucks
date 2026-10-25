package dev.njk.completion;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in Nunjucks tags, filters, tests and globals, with short descriptions for completion and docs. */
public final class NjkBuiltins {
    public record Item(@NotNull String name, @NotNull String signature, @NotNull String description) {
        public boolean takesArguments() {
            return signature.startsWith("(") && !signature.equals("()");
        }
    }

    public static final Map<String, Item> TAGS = items(
            "if", "condition", "Renders the body when the condition is truthy. Can have elif/else branches.",
            "elif", "condition", "Another branch of an if block.",
            "elseif", "condition", "Alias of elif.",
            "else", "", "Fallback branch of an if block, or of a for loop over an empty sequence.",
            "for", "item in items", "Loops over arrays, objects (k, v in obj) or anything iterable. Exposes `loop`.",
            "asyncEach", "item in items", "Like for, but supports async filters and templates; runs in series.",
            "asyncAll", "item in items", "Like asyncEach, but runs iterations in parallel.",
            "set", "name = value", "Assigns a variable. Without `=` captures the body up to endset.",
            "block", "name", "Defines a block that child templates can override.",
            "extends", "\"template\"", "Makes this template inherit from another one.",
            "include", "\"template\"", "Renders another template here, with the current context.",
            "import", "\"template\" as name", "Loads another template's macros and variables under a name.",
            "from", "\"template\" import name", "Loads specific macros from another template.",
            "macro", "name(args)", "Defines a reusable chunk of template, called like a function.",
            "call", "macro(args)", "Calls a macro, passing the body as `caller()`.",
            "filter", "name", "Applies a filter to the whole body.",
            "raw", "", "Outputs the body without processing Nunjucks syntax.",
            "verbatim", "", "Alias of raw.",
            "ifAsync", "condition", "Like if, but for use with async templates.");

    public static final Map<String, Item> FILTERS = items(
            "abs", "()", "Absolute value of a number.",
            "batch", "(size, fillWith)", "Splits a list into lists of the given size.",
            "capitalize", "()", "First character uppercase, the rest lowercase.",
            "center", "(width)", "Centers the value in a field of the given width.",
            "default", "(value, boolean)", "Fallback when the value is undefined (or falsy, when the second argument is true).",
            "d", "(value, boolean)", "Alias of default.",
            "dictsort", "(caseSensitive, by)", "Sorts an object and returns [key, value] pairs.",
            "dump", "(spaces)", "JSON.stringify the value.",
            "escape", "()", "HTML-escapes the value.",
            "e", "()", "Alias of escape.",
            "first", "()", "First item of a list.",
            "float", "(default)", "Converts to a floating point number.",
            "forceescape", "()", "Escapes even if the value is marked safe.",
            "groupby", "(attribute)", "Groups a list of objects by an attribute.",
            "indent", "(width, indentFirst)", "Indents each line.",
            "int", "(default)", "Converts to an integer.",
            "join", "(separator, attribute)", "Joins a list into a string.",
            "last", "()", "Last item of a list.",
            "length", "()", "Length of a string, array or object.",
            "list", "()", "Converts the value to a list.",
            "lower", "()", "Lowercases a string.",
            "nl2br", "()", "Replaces newlines with <br />.",
            "random", "()", "A random item from a list.",
            "reject", "(test, ...args)", "Removes items that pass the test.",
            "rejectattr", "(attribute)", "Removes objects whose attribute is truthy.",
            "replace", "(old, new, maxCount)", "Replaces occurrences of a string or regex.",
            "reverse", "()", "Reverses a string or list.",
            "round", "(precision, method)", "Rounds a number (method: common, ceil or floor).",
            "safe", "()", "Marks the value as safe, so it is not auto-escaped.",
            "select", "(test, ...args)", "Keeps items that pass the test.",
            "selectattr", "(attribute)", "Keeps objects whose attribute is truthy.",
            "slice", "(slices, fillWith)", "Splits a list into the given number of lists.",
            "sort", "(reverse, caseSensitive, attribute)", "Sorts a list.",
            "string", "()", "Converts the value to a string.",
            "striptags", "(preserveLinebreaks)", "Removes HTML tags and collapses whitespace.",
            "sum", "(attribute, start)", "Sums the items of a list.",
            "title", "()", "Title-cases each word.",
            "trim", "()", "Removes leading and trailing whitespace.",
            "truncate", "(length, killWords, end)", "Shortens a string to the given length.",
            "upper", "()", "Uppercases a string.",
            "urlencode", "()", "URL-encodes a string or an object as a query string.",
            "urlize", "(length, nofollow)", "Turns URLs in text into links.",
            "wordcount", "()", "Number of words in a string.");

    public static final Map<String, Item> ELEVENTY_FILTERS = items(
            "url", "()", "Eleventy: prefixes a URL with the path prefix.",
            "slugify", "()", "Eleventy: converts a string to a URL-friendly slug.",
            "log", "()", "Eleventy: console.log the value.",
            "inputPathToUrl", "()", "Eleventy: maps an input file path to its output URL.",
            "getCollectionItem", "(collection)", "Eleventy: the current page's item in a collection.",
            "getPreviousCollectionItem", "(collection)", "Eleventy: the previous item in a collection.",
            "getNextCollectionItem", "(collection)", "Eleventy: the next item in a collection.");

    public static final Map<String, Item> TESTS = items(
            "callable", "", "Value is a function.",
            "defined", "", "Value is not undefined.",
            "undefined", "", "Value is undefined.",
            "divisibleby", "(n)", "Value is divisible by n.",
            "escaped", "", "Value is marked safe.",
            "equalto", "(other)", "Value === other.",
            "eq", "(other)", "Alias of equalto.",
            "sameas", "(other)", "Alias of equalto.",
            "even", "", "Value is an even number.",
            "odd", "", "Value is an odd number.",
            "falsy", "", "Value is falsy.",
            "truthy", "", "Value is truthy.",
            "ge", "(other)", "Value >= other.",
            "gt", "(other)", "Value > other.",
            "greaterthan", "(other)", "Alias of gt.",
            "le", "(other)", "Value <= other.",
            "lt", "(other)", "Value < other.",
            "lessthan", "(other)", "Alias of lt.",
            "ne", "(other)", "Value !== other.",
            "lower", "", "String is all lowercase.",
            "upper", "", "String is all uppercase.",
            "null", "", "Value is null.",
            "number", "", "Value is a number.",
            "string", "", "Value is a string.",
            "iterable", "", "Value can be looped over.",
            "mapping", "", "Value is a plain object.");

    public static final Map<String, Item> GLOBALS = items(
            "range", "(start, stop, step)", "A list of numbers from start up to (not including) stop.",
            "cycler", "(...items)", "An object whose next() cycles through the items.",
            "joiner", "(separator)", "A function that returns the separator on every call but the first.");

    public static final Map<String, Item> ELEVENTY_GLOBALS = items(
            "page", "", "Eleventy: data about the current page (url, date, inputPath, fileSlug, ...).",
            "collections", "", "Eleventy: all collections, e.g. collections.all or collections.post.",
            "eleventy", "", "Eleventy: version and environment information.",
            "content", "", "Eleventy: the rendered content of the page using this layout.",
            "pagination", "", "Eleventy: pagination data, when the page paginates.");

    public static final Map<String, Item> LOOP_MEMBERS = items(
            "index", "", "Current iteration, starting at 1.",
            "index0", "", "Current iteration, starting at 0.",
            "revindex", "", "Iterations until the end, ending at 1.",
            "revindex0", "", "Iterations until the end, ending at 0.",
            "first", "", "True on the first iteration.",
            "last", "", "True on the last iteration.",
            "length", "", "Total number of items.");

    private NjkBuiltins() {
    }

    private static Map<String, Item> items(String... data) {
        Map<String, Item> map = new LinkedHashMap<>();
        for (int i = 0; i + 2 < data.length; i += 3) {
            map.put(data[i], new Item(data[i], data[i + 1], data[i + 2]));
        }
        return Collections.unmodifiableMap(map);
    }
}
