package dev.njk.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.NjkFile;
import dev.njk.NjkLanguage;
import dev.njk.index.NjkSourceScanner;
import dev.njk.lexer.NjkTokenTypes;
import dev.njk.parser.NjkElementTypes;
import dev.njk.parser.NjkTagSpec;
import dev.njk.psi.*;
import dev.njk.resolve.*;
import dev.njk.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.*;

import static dev.njk.lexer.NjkTokenTypes.*;

public final class NjkCompletionContributor extends CompletionContributor {
    private static final int PRIORITY_CLOSING_TAG = 100;
    private static final int PRIORITY_LOCAL = 50;
    private static final int PRIORITY_CONTEXT = 30;
    private static final int PRIORITY_BUILTIN = 10;

    private static final List<String> EXPRESSION_KEYWORDS = List.of("and", "or", "not", "in", "is", "if", "else");

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        if (!position.getLanguage().is(NjkLanguage.INSTANCE)) return;
        IElementType type = position.getNode().getElementType();
        PsiElement parent = position.getParent();

        if (type == TAG_NAME) {
            completeTags(position, result);
        } else if (type == STRING) {
            if (parent instanceof NjkStringLiteral literal && literal.getTemplateTag() != null) {
                completeTemplatePaths(literal, parameters, result);
            }
        } else if (type == TEMPLATE_TEXT || type == COMMENT || type == FRONT_MATTER) {
            return;
        } else if (parent instanceof NjkFilterName) {
            completeFilters(position.getProject(), result);
        } else if (parent instanceof NjkTestName) {
            for (NjkBuiltins.Item test : NjkBuiltins.TESTS.values()) result.addElement(builtin(test, "test", AllIcons.Nodes.Function));
        } else if (parent instanceof NjkMemberExpression member && member.getNameElement() == position) {
            completeMembers(member, result);
        } else if (parent instanceof NjkImportedName imported) {
            NjkTag tag = imported.getFromTag();
            if (tag != null && tag.getNode().findChildByType(KW_IMPORT) == null) {
                result.addElement(keyword("import"));
            } else {
                completeImportedNames(imported, result);
            }
        } else if (parent instanceof NjkBlockName block) {
            completeBlockNames(block, result);
        } else if (parent instanceof NjkVariable variable) {
            if (variable.getKind() == NjkVariable.Kind.IMPORT_ALIAS
                    && variable.getDeclaringTag().getNode().findChildByType(KW_AS) == null) {
                result.addElement(keyword("as"));
            }
        } else if (parent instanceof NjkReferenceExpression reference) {
            if (followsLoopVariable(reference)) {
                result.addElement(keyword("in"));
                return;
            }
            completeVariables(reference, parameters, result);
        } else if (parent instanceof PsiErrorElement) {
            completeTagKeywords(position, result);
        }
    }

    @Override
    public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
        if (typeChar != '|' && typeChar != '.') return false;
        if (!position.getLanguage().is(NjkLanguage.INSTANCE)) return false;
        IElementType type = position.getNode().getElementType();
        return type != TEMPLATE_TEXT && type != COMMENT && type != STRING && type != FRONT_MATTER && type != NUMBER;
    }

    // ------------------------------------------------------------------ tags

    private static void completeTags(PsiElement position, CompletionResultSet result) {
        Set<String> added = new HashSet<>();
        NjkStatement statement = PsiTreeUtil.getParentOfType(position, NjkStatement.class);
        NjkTag currentTag = PsiTreeUtil.getParentOfType(position, NjkTag.class);
        if (statement != null && statement.getOpenTag() == currentTag) {
            statement = PsiTreeUtil.getParentOfType(statement, NjkStatement.class);
        }
        if (statement != null) {
            String name = statement.getTagName();
            NjkTagSpec spec = name == null ? null
                    : statement.is(NjkElementTypes.SET_STATEMENT) ? NjkTagSpec.SET_BLOCK
                    : NjkTagSpec.block(name);
            String end = spec != null ? spec.end() : name != null ? "end" + name : null;
            if (end != null && statement.getEndTag() == null && added.add(end)) {
                result.addElement(PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(end).bold().withTypeText("closes " + name), PRIORITY_CLOSING_TAG));
            }
            if (spec != null) {
                for (String continuation : spec.continuations()) {
                    NjkBuiltins.Item item = NjkBuiltins.TAGS.get(continuation);
                    if (item != null && added.add(continuation)) {
                        result.addElement(PrioritizedLookupElement.withPriority(tag(item), PRIORITY_LOCAL));
                    }
                }
            }
        }
        for (NjkBuiltins.Item item : NjkBuiltins.TAGS.values()) {
            if (NjkTagSpec.isEndOrContinuation(item.name()) || !added.add(item.name())) continue;
            result.addElement(PrioritizedLookupElement.withPriority(tag(item), PRIORITY_BUILTIN));
        }
        Project project = position.getProject();
        for (String name : NjkLookup.names(project, NjkSourceScanner.TAG)) {
            if (added.add(name)) result.addElement(customTag(name, "shortcode"));
        }
        for (String name : NjkLookup.names(project, NjkSourceScanner.PAIRED_TAG)) {
            if (added.add(name)) result.addElement(customTag(name, "paired shortcode"));
        }
    }

    private static LookupElement tag(NjkBuiltins.Item item) {
        LookupElementBuilder builder = LookupElementBuilder.create(item.name()).bold()
                .withTailText(item.signature().isEmpty() ? "" : " " + item.signature(), true)
                .withTypeText("tag");
        if (!item.signature().isEmpty()) builder = builder.withInsertHandler(SPACE_AFTER);
        return builder;
    }

    private static LookupElement customTag(String name, String kind) {
        return LookupElementBuilder.create(name).withTypeText(kind).withIcon(AllIcons.Nodes.Tag).withInsertHandler(SPACE_AFTER);
    }

    private static final InsertHandler<LookupElement> SPACE_AFTER = (context, item) -> {
        Document document = context.getDocument();
        int offset = context.getTailOffset();
        CharSequence text = document.getCharsSequence();
        if (offset >= text.length() || text.charAt(offset) != ' ') {
            document.insertString(offset, " ");
        }
        context.getEditor().getCaretModel().moveToOffset(offset + 1);
    };

    private static void completeTagKeywords(PsiElement position, CompletionResultSet result) {
        NjkTag tag = PsiTreeUtil.getParentOfType(position, NjkTag.class);
        String name = tag != null ? tag.getTagName() : null;
        if (name != null) {
            switch (name) {
                case "include" -> {
                    result.addElement(keyword("ignore missing"));
                    result.addElement(keyword("with context"));
                    result.addElement(keyword("without context"));
                    return;
                }
                case "import", "from" -> {
                    result.addElement(keyword("with context"));
                    result.addElement(keyword("without context"));
                    return;
                }
                default -> {
                }
            }
        }
        for (String kw : EXPRESSION_KEYWORDS) result.addElement(keyword(kw));
    }

    private static boolean followsLoopVariable(NjkReferenceExpression reference) {
        if (!(reference.getParent() instanceof NjkTag tag) || !tag.isLoop()) return false;
        if (tag.getNode().findChildByType(KW_IN) != null) return false;
        PsiElement prev = PsiTreeUtil.skipSiblingsBackward(reference, PsiWhiteSpace.class, PsiErrorElement.class);
        return prev instanceof NjkVariable;
    }

    // ------------------------------------------------------------------ expressions

    private static void completeFilters(Project project, CompletionResultSet result) {
        Set<String> added = new HashSet<>();
        for (String name : NjkLookup.names(project, NjkSourceScanner.FILTER)) {
            if (!added.add(name)) continue;
            LookupElementBuilder builder = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Function);
            List<NjkSourceTarget> registrations = NjkLookup.targets(project, NjkSourceScanner.FILTER + name, "filter");
            NjkTypeProvider.FunctionInfo info = registrations.isEmpty() ? null : NjkTypes.functionInfo(registrations.get(0));
            if (info != null) {
                builder = builder.withTailText(info.parameters(), true).withTypeText(info.returnTypeText(), true);
            } else {
                builder = builder.withTypeText("custom filter");
            }
            result.addElement(PrioritizedLookupElement.withPriority(builder, PRIORITY_CONTEXT));
        }
        if (NjkEleventy.isEleventyProject(project)) {
            for (NjkBuiltins.Item item : NjkBuiltins.ELEVENTY_FILTERS.values()) {
                if (added.add(item.name())) result.addElement(builtin(item, "Eleventy", AllIcons.Nodes.Function));
            }
        }
        for (NjkBuiltins.Item item : NjkBuiltins.FILTERS.values()) {
            if (added.add(item.name())) result.addElement(builtin(item, "filter", AllIcons.Nodes.Function));
        }
    }

    private static void completeVariables(NjkReferenceExpression reference, CompletionParameters parameters,
                                          CompletionResultSet result) {
        Set<String> added = new HashSet<>();
        NjkScopes.processLocal(reference, declaration -> {
            String name = declaration.getName();
            if (name != null && !name.equals(reference.getName()) && added.add(name)) {
                result.addElement(PrioritizedLookupElement.withPriority(declarationElement(declaration, name), PRIORITY_LOCAL));
            }
            return true;
        });

        if (NjkScopes.enclosingStatement(reference, NjkElementTypes.FOR_STATEMENT) != null && added.add("loop")) {
            result.addElement(special("loop", "loop state (index, first, last, ...)"));
        }
        if (NjkScopes.enclosingStatement(reference, NjkElementTypes.MACRO_STATEMENT) != null) {
            if (added.add("caller")) result.addElement(special("caller", "body of the calling {% call %}"));
            if (added.add("varargs")) result.addElement(special("varargs", "extra positional arguments"));
            if (added.add("kwargs")) result.addElement(special("kwargs", "extra keyword arguments"));
        }
        if (NjkScopes.enclosingStatement(reference, NjkElementTypes.BLOCK_STATEMENT) != null && added.add("super")) {
            result.addElement(special("super", "parent block content").withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS));
        }

        PsiFile original = parameters.getOriginalFile().getViewProvider().getPsi(NjkLanguage.INSTANCE);
        if (original instanceof NjkFile file) {
            for (Map.Entry<String, List<NjkContext.Source>> entry : NjkContext.external(file).entrySet()) {
                String name = entry.getKey();
                if (entry.getValue().isEmpty() || !added.add(name)) continue;
                NjkContext.Source source = entry.getValue().get(0);
                String origin = source.description() + (entry.getValue().size() > 1 ? " (+" + (entry.getValue().size() - 1) + ")" : "");
                NjkType type = NjkTypes.typeOfDeclaration(source.target());
                LookupElementBuilder builder = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable)
                        .withTypeText(origin, true);
                if (type != null) builder = builder.withTailText(": " + type.presentableText(), true);
                result.addElement(PrioritizedLookupElement.withPriority(builder, PRIORITY_CONTEXT));
            }
            for (NjkTypeAnnotations.Annotation annotation : NjkTypeAnnotations.annotations(file)) {
                if (!added.add(annotation.name())) continue;
                result.addElement(PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(annotation.name()).withIcon(AllIcons.Nodes.Variable)
                                .withTailText(": " + annotation.typeText(), true).withTypeText("@type", true),
                        PRIORITY_CONTEXT));
            }
        }

        Project project = reference.getProject();
        for (NjkBuiltins.Item item : NjkBuiltins.GLOBALS.values()) {
            if (added.add(item.name())) {
                result.addElement(builtin(item, "global", AllIcons.Nodes.Function)
                        .withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS));
            }
        }
        if (NjkEleventy.isEleventyProject(project)) {
            for (NjkBuiltins.Item item : NjkBuiltins.ELEVENTY_GLOBALS.values()) {
                if (added.add(item.name())) result.addElement(builtin(item, "Eleventy", AllIcons.Nodes.Variable));
            }
        }
        for (String kw : List.of("true", "false", "none", "not")) result.addElement(keyword(kw));
    }

    private static LookupElement declarationElement(PsiNamedElement declaration, String name) {
        LookupElementBuilder builder = LookupElementBuilder.create(declaration, name).withIcon(declaration.getIcon(0));
        if (declaration instanceof NjkNamedElementBase named) builder = builder.withTypeText(named.getKindDescription());
        if (declaration instanceof NjkVariable v && v.getKind() == NjkVariable.Kind.MACRO) {
            builder = builder.withTailText(macroParameters(v), true).withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
        }
        if (declaration instanceof NjkImportedName || declaration instanceof NjkVariable v2 && v2.getKind() == NjkVariable.Kind.FROM_ALIAS) {
            builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
        }
        return builder;
    }

    private static String macroParameters(NjkVariable macro) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (NjkVariable param : macro.getDeclaringTag().getParameters()) joiner.add(param.getName());
        return joiner.toString();
    }

    private static void completeMembers(NjkMemberExpression member, CompletionResultSet result) {
        NjkType qualifier = NjkTypes.typeOf(member.getQualifier());
        if (qualifier == null) return;
        int priority = 0;
        for (NjkMember m : qualifier.members().values()) {
            PsiElement declaration = m.declaration();
            Icon icon = m.callable() ? AllIcons.Nodes.Method : AllIcons.Nodes.Property;
            LookupElementBuilder builder = (declaration != null
                    ? LookupElementBuilder.create(declaration, m.name())
                    : LookupElementBuilder.create(m.name()))
                    .withIcon(icon)
                    .withTypeText(m.typeText(), true);
            if (declaration instanceof NjkVariable v && v.getKind() == NjkVariable.Kind.MACRO) {
                builder = builder.withTailText(macroParameters(v), true).withTypeText("macro");
            }
            if (m.callable()) builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
            // keep declaration order rather than sorting own members among inherited ones
            result.addElement(PrioritizedLookupElement.withPriority(builder, PRIORITY_LOCAL - Math.min(priority++, 40) / 10.0));
        }
    }

    private static void completeImportedNames(NjkImportedName imported, CompletionResultSet result) {
        NjkTag tag = imported.getFromTag();
        if (tag == null) return;
        for (NjkFile file : NjkLookup.targetsOf(tag)) {
            for (NjkVariable macro : NjkScopes.macros(file)) {
                result.addElement(LookupElementBuilder.create(macro, macro.getName()).withIcon(AllIcons.Nodes.Function)
                        .withTailText(macroParameters(macro), true).withTypeText(file.getName()));
            }
        }
    }

    private static void completeBlockNames(NjkBlockName block, CompletionResultSet result) {
        if (!(block.getContainingFile().getOriginalFile() instanceof NjkFile file)) return;
        Set<String> added = new HashSet<>();
        for (NjkFile parent : NjkLookup.parentTemplates(file)) {
            for (NjkBlockName candidate : PsiTreeUtil.findChildrenOfType(parent, NjkBlockName.class)) {
                String name = candidate.getName();
                if (candidate.isDefinition() && name != null && added.add(name)) {
                    result.addElement(LookupElementBuilder.create(candidate, name).withIcon(AllIcons.Nodes.Tag)
                            .withTypeText(parent.getName()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ template paths

    private static void completeTemplatePaths(NjkStringLiteral literal, CompletionParameters parameters,
                                              CompletionResultSet result) {
        int start = literal.getTextRange().getStartOffset() + literal.getValueRange().getStartOffset();
        int caret = parameters.getOffset();
        String text = literal.getContainingFile().getText();
        if (caret < start || caret > text.length()) return;
        String prefix = text.substring(start, caret);
        CompletionResultSet paths = result.withPrefixMatcher(prefix);
        for (Map.Entry<String, VirtualFile> entry : NjkTemplatePaths.completionVariants(parameters.getOriginalFile()).entrySet()) {
            VirtualFile file = entry.getValue();
            Icon icon = file.getFileType().getIcon();
            paths.addElement(LookupElementBuilder.create(entry.getKey()).withLookupString(file.getName())
                    .withIcon(icon).withTypeText(file.getParent() != null ? file.getParent().getName() : null, true));
        }
        paths.stopHere();
    }

    // ------------------------------------------------------------------ helpers

    private static LookupElementBuilder builtin(NjkBuiltins.Item item, String type, @Nullable Icon icon) {
        return LookupElementBuilder.create(item.name())
                .withIcon(icon)
                .withTailText(item.signature().startsWith("(") ? item.signature() : "", true)
                .withTypeText(type);
    }

    private static LookupElementBuilder special(String name, String description) {
        return LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable).withTypeText(description, true).bold();
    }

    private static LookupElement keyword(String text) {
        return LookupElementBuilder.create(text).bold();
    }
}
