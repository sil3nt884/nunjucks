package dev.njk.parser;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Describes a built-in tag that opens a block: its closing tag and the tags allowed in between. */
public record NjkTagSpec(@NotNull String name, @NotNull String end, @NotNull Set<String> continuations,
                         @NotNull IElementType statementType) {

    private static final Set<String> IF_CONTINUATIONS = Set.of("elif", "elseif", "else");
    private static final Map<String, NjkTagSpec> BLOCKS = new LinkedHashMap<>();

    public static final NjkTagSpec SET_BLOCK = new NjkTagSpec("set", "endset", Set.of(), NjkElementTypes.SET_STATEMENT);

    /** Tags that never have a body (set only has one when written without {@code =}). */
    public static final Set<String> SIMPLE_TAGS = Set.of("extends", "include", "import", "from", "set");

    static {
        register(new NjkTagSpec("if", "endif", IF_CONTINUATIONS, NjkElementTypes.IF_STATEMENT));
        register(new NjkTagSpec("ifAsync", "endif", IF_CONTINUATIONS, NjkElementTypes.IF_STATEMENT));
        register(new NjkTagSpec("for", "endfor", Set.of("else"), NjkElementTypes.FOR_STATEMENT));
        register(new NjkTagSpec("asyncEach", "endeach", Set.of("else"), NjkElementTypes.FOR_STATEMENT));
        register(new NjkTagSpec("asyncAll", "endall", Set.of("else"), NjkElementTypes.FOR_STATEMENT));
        register(new NjkTagSpec("block", "endblock", Set.of(), NjkElementTypes.BLOCK_STATEMENT));
        register(new NjkTagSpec("macro", "endmacro", Set.of(), NjkElementTypes.MACRO_STATEMENT));
        register(new NjkTagSpec("call", "endcall", Set.of(), NjkElementTypes.CALL_STATEMENT));
        register(new NjkTagSpec("filter", "endfilter", Set.of(), NjkElementTypes.FILTER_STATEMENT));
        register(new NjkTagSpec("raw", "endraw", Set.of(), NjkElementTypes.RAW_STATEMENT));
        register(new NjkTagSpec("verbatim", "endverbatim", Set.of(), NjkElementTypes.RAW_STATEMENT));
    }

    private static void register(NjkTagSpec spec) {
        BLOCKS.put(spec.name, spec);
    }

    public static @Nullable NjkTagSpec block(@NotNull String name) {
        return BLOCKS.get(name);
    }

    public static Map<String, NjkTagSpec> blocks() {
        return BLOCKS;
    }

    public static boolean isEndOrContinuation(@NotNull String name) {
        if (IF_CONTINUATIONS.contains(name)) return true;
        if (name.equals("endset")) return true;
        for (NjkTagSpec spec : BLOCKS.values()) {
            if (spec.end.equals(name)) return true;
        }
        return false;
    }

    public static boolean isBuiltin(@NotNull String name) {
        return BLOCKS.containsKey(name) || SIMPLE_TAGS.contains(name) || isEndOrContinuation(name);
    }
}
