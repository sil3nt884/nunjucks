package dev.njk.highlight;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import dev.njk.NjkIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

public final class NjkColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Delimiters//Tag delimiters", NjkHighlightColors.DELIMITER),
            new AttributesDescriptor("Tags//Built-in tag", NjkHighlightColors.TAG_NAME),
            new AttributesDescriptor("Tags//Custom tag or shortcode", NjkHighlightColors.CUSTOM_TAG),
            new AttributesDescriptor("Tags//Block name", NjkHighlightColors.BLOCK_NAME),
            new AttributesDescriptor("Keyword", NjkHighlightColors.KEYWORD),
            new AttributesDescriptor("Identifiers//Variable", NjkHighlightColors.IDENTIFIER),
            new AttributesDescriptor("Identifiers//Variable declaration", NjkHighlightColors.VARIABLE_DECLARATION),
            new AttributesDescriptor("Identifiers//Macro declaration", NjkHighlightColors.MACRO_DECLARATION),
            new AttributesDescriptor("Identifiers//Parameter", NjkHighlightColors.PARAMETER),
            new AttributesDescriptor("Identifiers//Member", NjkHighlightColors.MEMBER),
            new AttributesDescriptor("Identifiers//Function or macro call", NjkHighlightColors.FUNCTION_CALL),
            new AttributesDescriptor("Identifiers//Filter", NjkHighlightColors.FILTER),
            new AttributesDescriptor("Identifiers//Test", NjkHighlightColors.TEST),
            new AttributesDescriptor("Literals//String", NjkHighlightColors.STRING),
            new AttributesDescriptor("Literals//Number", NjkHighlightColors.NUMBER),
            new AttributesDescriptor("Literals//Regular expression", NjkHighlightColors.REGEX),
            new AttributesDescriptor("Comment", NjkHighlightColors.COMMENT),
            new AttributesDescriptor("Front matter", NjkHighlightColors.FRONT_MATTER),
            new AttributesDescriptor("Operators//Operator", NjkHighlightColors.OPERATOR),
            new AttributesDescriptor("Operators//Parentheses", NjkHighlightColors.PARENTHESES),
            new AttributesDescriptor("Operators//Brackets", NjkHighlightColors.BRACKETS),
            new AttributesDescriptor("Operators//Braces", NjkHighlightColors.BRACES),
            new AttributesDescriptor("Operators//Dot", NjkHighlightColors.DOT),
            new AttributesDescriptor("Operators//Comma", NjkHighlightColors.COMMA),
            new AttributesDescriptor("Bad character", NjkHighlightColors.BAD_CHARACTER),
    };

    private static final Map<String, TextAttributesKey> TAGS = Map.of(
            "filter", NjkHighlightColors.FILTER,
            "test", NjkHighlightColors.TEST,
            "decl", NjkHighlightColors.VARIABLE_DECLARATION,
            "macro", NjkHighlightColors.MACRO_DECLARATION,
            "param", NjkHighlightColors.PARAMETER,
            "member", NjkHighlightColors.MEMBER,
            "call", NjkHighlightColors.FUNCTION_CALL,
            "block", NjkHighlightColors.BLOCK_NAME,
            "custom", NjkHighlightColors.CUSTOM_TAG);

    @Override
    public @Nullable Icon getIcon() {
        return NjkIcons.FILE;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new NjkSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                ---
                title: Blog
                ---
                {% extends "layouts/base.njk" %}
                {% from "macros/forms.njk" import <call>field</call> %}
                {# Comments look like this #}
                {% macro <macro>card</macro>(<param>post</param>, <param>compact</param>=false) %}
                  <article class="card">
                    <h2>{{ <param>post</param>.<member>title</member> | <filter>title</filter> }}</h2>
                    {% if <param>post</param>.<member>tags</member> is <test>defined</test> and not <param>compact</param> %}
                      {{ <param>post</param>.<member>tags</member> | <filter>join</filter>(", ") }}
                    {% endif %}
                  </article>
                {% endmacro %}

                {% block <block>content</block> %}
                  {% set <decl>total</decl> = posts | <filter>length</filter> %}
                  {% set <decl>slug</decl> = title | <filter>lower</filter> | <filter>replace</filter>(r/[^a-z0-9]+/g, "-") %}
                  {% for <decl>post</decl> in posts %}
                    {{ <call>card</call>(post, compact=loop.<member>index</member> > 3) }}
                  {% else %}
                    <p>No posts yet ({{ total * 2 + 1 }}).</p>
                  {% endfor %}
                  {% <custom>image</custom> "cover.png", "Cover" %}
                {% endblock %}
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAGS;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Nunjucks";
    }
}
