package dev.njk.javascript;

import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.stubs.JSClassIndex;
import com.intellij.lang.javascript.psi.types.JSContext;
import com.intellij.lang.javascript.psi.types.JSNamedTypeFactory;
import com.intellij.lang.javascript.psi.types.JSTypeSourceFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import dev.njk.resolve.NjkSourceTarget;
import dev.njk.types.NjkTemplateTypes;
import dev.njk.types.NjkType;
import dev.njk.types.NjkTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Uses the JavaScript plugin's type evaluation to give templates TypeScript types. */
public final class NjkJsTypeProvider implements NjkTypeProvider {
    private static final Logger LOG = Logger.getInstance(NjkJsTypeProvider.class);
    private static final Pattern IMPORT_TYPE = Pattern.compile("import\\(\\s*([\"'])([^\"']+)\\1\\s*\\)\\.");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Pattern ARRAY_TYPE = Pattern.compile("(?:Readonly)?Array\\s*<(.+)>");
    private static final List<String> MODULE_SUFFIXES = List.of("", ".ts", ".d.ts", ".tsx", ".js", ".mjs",
            "/index.ts", "/index.d.ts", "/index.js");

    @Override
    public @Nullable NjkType typeOf(@NotNull PsiElement target) {
        PsiElement element = target;
        if (target instanceof NjkSourceTarget source) {
            element = valueAt(source.getContainingFile(), source.getTextOffset());
            if (element == null) return null;
        }
        if (!(element instanceof JSElement)) return null;
        return NjkJsType.wrap(typeOfJs(element), element);
    }

    private static @Nullable JSType typeOfJs(PsiElement element) {
        if (element instanceof JSProperty property) {
            JSExpression value = property.getValue();
            if (value != null) return JSResolveUtil.getExpressionJSType(value);
            return property.getJSType();
        }
        if (element instanceof JSExpression expression) return JSResolveUtil.getExpressionJSType(expression);
        if (element instanceof JSTypeOwner owner) return owner.getJSType();
        return JSResolveUtil.getElementJSType(element);
    }

    /**
     * The value that the name at {@code offset} stands for: the property in a render() context object,
     * the right side of {@code app.locals.x = ...}, or the second argument of {@code addGlobal("x", ...)}.
     */
    private static @Nullable PsiElement valueAt(PsiFile file, int offset) {
        if (!(file instanceof JSFile)) return null;
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null) return null;
        JSProperty property = PsiTreeUtil.getParentOfType(leaf, JSProperty.class, false);
        if (property != null && property.getNameIdentifier() != null
                && property.getNameIdentifier().getTextRange().contains(offset)) {
            return property;
        }
        JSLiteralExpression literal = PsiTreeUtil.getParentOfType(leaf, JSLiteralExpression.class, false);
        JSCallExpression call = NjkJsPsi.callWithFirstArgument(literal);
        if (call != null) return NjkJsPsi.argument(call, 1);
        JSAssignmentExpression assignment = PsiTreeUtil.getParentOfType(leaf, JSAssignmentExpression.class);
        return assignment != null ? assignment.getROperand() : null;
    }

    @Override
    public @Nullable List<ContextVariable> renderContext(@NotNull PsiFile script, int pathOffset) {
        if (!(script instanceof JSFile)) return null;
        JSCallExpression call = NjkJsPsi.callWithFirstArgument(NjkJsPsi.literalAt(script, pathOffset));
        if (call == null) return null;
        List<ContextVariable> result = new ArrayList<>();
        JSExpression context = NjkJsPsi.argument(call, 1);
        if (context == null) return result;
        String description = "render() context in " + script.getName();
        if (context instanceof JSObjectLiteralExpression object) {
            for (JSElement element : object.getPropertiesIncludingSpreads()) {
                if (element instanceof JSProperty property && property.getName() != null) {
                    result.add(new ContextVariable(property.getName(), property, description));
                } else if (element instanceof JSSpreadExpression spread && spread.getExpression() != null) {
                    addProperties(JSResolveUtil.getExpressionJSType(spread.getExpression()), spread, description, result);
                }
            }
        } else {
            JSType type = JSResolveUtil.getExpressionJSType(context);
            String typed = type != null ? description + " (" + NjkJsType.text(type) + ")" : description;
            addProperties(type, context, typed, result);
        }
        return result;
    }

    private static void addProperties(@Nullable JSType type, PsiElement context, String description,
                                      List<ContextVariable> result) {
        if (type == null || JSTypeUtils.isAnyType(type)) return;
        for (JSRecordType.PropertySignature property : type.asRecordType(context).getProperties()) {
            if (property.isPrivateName() || !property.hasValidName()) continue;
            PsiElement declaration = property.getMemberSource().getSingleElement();
            result.add(new ContextVariable(property.getMemberName(), declaration != null ? declaration : context, description));
        }
    }

    @Override
    public @Nullable FunctionInfo functionInfo(@NotNull NjkSourceTarget registration) {
        PsiFile file = registration.getContainingFile();
        if (!(file instanceof JSFile)) return null;
        JSCallExpression call = NjkJsPsi.callWithFirstArgument(NjkJsPsi.literalAt(file, registration.getTextOffset()));
        JSExpression function = call != null ? NjkJsPsi.argument(call, 1) : null;
        if (function == null) return null;
        JSType type = JSResolveUtil.getExpressionJSType(function);
        if (type == null) return null;
        JSFunctionType functionType = JSTypeUtils.getFunctionType(type, false, function)
                .filter(JSFunctionType.class::isInstance).map(JSFunctionType.class::cast).findFirst().orElse(null);
        if (functionType == null) return null;
        String method = NjkJsPsi.methodName(call);
        // a filter's first parameter is the value on the left of the pipe
        boolean skipFirst = method != null && method.contains("Filter");
        StringJoiner parameters = new StringJoiner(", ", "(", ")");
        List<JSParameterTypeDecorator> params = functionType.getParameters();
        for (int i = skipFirst ? 1 : 0; i < params.size(); i++) {
            JSParameterTypeDecorator param = params.get(i);
            String name = param.getName() != null ? param.getName() : "arg" + i;
            JSType paramType = param.getSimpleType();
            parameters.add((param.isRest() ? "..." : "") + name + (param.isOptional() ? "?" : "")
                    + (paramType != null ? ": " + NjkJsType.text(paramType) : ""));
        }
        JSType returnType = functionType.getReturnType();
        return new FunctionInfo(parameters.toString(), NjkJsType.wrap(returnType, function),
                returnType != null ? NjkJsType.text(returnType) : "");
    }

    /**
     * Evaluates annotation types: {@code User}, {@code User[]}, {@code Array<User>} and
     * {@code import("../src/models").User}, where {@code User} is an interface, class or type alias.
     */
    @Override
    public @Nullable NjkType resolveTypeText(@NotNull String typeText, @NotNull PsiElement context) {
        if (DumbService.isDumb(context.getProject())) return null;
        String text = typeText.trim();
        if (text.endsWith("[]")) {
            NjkType element = resolveTypeText(text.substring(0, text.length() - 2), context);
            return element != null ? new NjkTemplateTypes.ArrayOf(element) : null;
        }
        Matcher array = ARRAY_TYPE.matcher(text);
        if (array.matches()) {
            NjkType element = resolveTypeText(array.group(1), context);
            return element != null ? new NjkTemplateTypes.ArrayOf(element) : null;
        }
        PsiFile module = null;
        Matcher importType = IMPORT_TYPE.matcher(text);
        if (importType.lookingAt()) {
            module = findModule(context, importType.group(2));
            if (module == null) return null;
            text = text.substring(importType.end());
        }
        if (!IDENTIFIER.matcher(text).matches()) return null;
        PsiElement declaration = module != null ? findDeclaration(module, text) : findDeclaration(context.getProject(), text);
        return declaration != null ? declarationType(declaration) : null;
    }

    private static @Nullable NjkType declarationType(PsiElement declaration) {
        try {
            JSType type;
            if (declaration instanceof JSClass cls && cls.getQualifiedName() != null) {
                type = JSNamedTypeFactory.createType(cls.getQualifiedName(),
                        JSTypeSourceFactory.createTypeSource(cls, true), JSContext.INSTANCE);
            } else {
                type = JSResolveUtil.getElementJSType(declaration);
            }
            return NjkJsType.wrap(type, declaration);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.debug("Can't evaluate type of " + declaration, e);
            return null;
        }
    }

    private static @Nullable PsiElement findDeclaration(PsiFile module, String name) {
        for (JSQualifiedNamedElement element : PsiTreeUtil.findChildrenOfType(module, JSQualifiedNamedElement.class)) {
            if (name.equals(element.getName()) && (element instanceof JSClass || element instanceof TypeScriptTypeAlias)) {
                return element;
            }
        }
        return null;
    }

    private static @Nullable PsiElement findDeclaration(Project project, String name) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (JSPsiElementBase element : JSClassIndex.getElements(name, project, scope)) {
            if (element instanceof JSClass) return element;
        }
        return typeAliases(project).get(name);
    }

    /** Top-level type aliases in project TypeScript files, which the class index doesn't cover. */
    private static Map<String, PsiElement> typeAliases(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            Map<String, PsiElement> result = new HashMap<>();
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            for (VirtualFile vf : FileTypeIndex.getFiles(TypeScriptFileType.INSTANCE, scope)) {
                PsiFile file = PsiManager.getInstance(project).findFile(vf);
                if (file == null) continue;
                for (TypeScriptTypeAlias alias : PsiTreeUtil.findChildrenOfType(file, TypeScriptTypeAlias.class)) {
                    if (alias.getName() != null) result.putIfAbsent(alias.getName(), alias);
                }
            }
            return CachedValueProvider.Result.create(result, PsiModificationTracker.getInstance(project));
        });
    }

    private static @Nullable PsiFile findModule(PsiElement context, String path) {
        PsiFile file = context.getContainingFile();
        VirtualFile vf = file != null ? file.getOriginalFile().getVirtualFile() : null;
        VirtualFile dir = vf != null ? vf.getParent() : null;
        if (dir == null) return null;
        for (String suffix : MODULE_SUFFIXES) {
            VirtualFile module = dir.findFileByRelativePath(path + suffix);
            if (module != null && !module.isDirectory()) return PsiManager.getInstance(context.getProject()).findFile(module);
        }
        return null;
    }
}
