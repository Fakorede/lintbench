package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";
    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ATTR_CONTEXT = "context";
    private static final String VIEW_CLASS = "android.view.View";
    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String FRAGMENT_CLASS = "android.app.Fragment";
    private static final String SUPPORT_FRAGMENT_CLASS = "android.support.v4.app.Fragment";
    private static final String ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment";

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute must specify the name of a public method "
                            + "in the view's context that takes a single `android.view.View` "
                            + "parameter and is invoked when the view is clicked.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, Set<String>> mMethods = new HashMap<>();
    private final Map<String, String> mContextClassesByFile = new HashMap<>();
    private final List<OnClickInfo> mReferences = new ArrayList<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (OnClickInfo info : mReferences) {
            String contextName = mContextClassesByFile.get(info.filePath);
            boolean found;
            String reportedContext = null;

            if (contextName != null && !contextName.isEmpty()) {
                reportedContext = contextName;
                String fqcn = resolveContextClass(contextName);
                if (fqcn != null) {
                    found = isMethodInClass(fqcn, info.methodName);
                } else {
                    found = isMethodInAnyContext(info.methodName);
                }
            } else {
                found = isMethodInAnyContext(info.methodName);
            }

            if (!found) {
                String message = "The `onClick` method `" + info.methodName + "` does not exist";
                if (reportedContext != null) {
                    message += " in the configured context `" + reportedContext + "`";
                }
                message +=
                        ". It must be a public method that takes exactly one `View` parameter.";
                context.report(ISSUE, info.location, message);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ON_CLICK, ATTR_CONTEXT);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            return;
        }

        String namespaceUri = attribute.getNamespaceURI();
        if (ATTR_CONTEXT.equals(localName) && TOOLS_URI.equals(namespaceUri)) {
            String value = attribute.getValue();
            if (value != null) {
                mContextClassesByFile.put(context.file.getPath(), value.trim());
            }
        } else if (ATTR_ON_CLICK.equals(localName) && ANDROID_URI.equals(namespaceUri)) {
            String value = attribute.getValue();
            if (value != null && !value.isEmpty()) {
                mReferences.add(
                        new OnClickInfo(
                                value, context.getValueLocation(attribute), context.file.getPath()));
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ACTIVITY_CLASS,
                FRAGMENT_CLASS,
                SUPPORT_FRAGMENT_CLASS,
                ANDROIDX_FRAGMENT_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        Set<String> methods = new HashSet<>();
        collectOnClickMethods(psiClass, methods);
        if (!methods.isEmpty()) {
            mMethods.put(className, methods);
        }
    }

    private void collectOnClickMethods(@NonNull PsiClass psiClass, @NonNull Set<String> methods) {
        for (PsiMethod method : psiClass.getMethods()) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)
                    || method.isConstructor()
                    || method.getParameterList().getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = method.getParameterList().getParameters()[0];
            PsiType type = parameter.getType();
            if (type != null && VIEW_CLASS.equals(type.getCanonicalText())) {
                methods.add(method.getName());
            }
        }

        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
            collectOnClickMethods(superClass, methods);
        }
    }

    private String resolveContextClass(@NonNull String contextName) {
        String trimmed = contextName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }

        if (trimmed.contains(".")) {
            if (mMethods.containsKey(trimmed)) {
                return trimmed;
            }
            return null;
        }

        String suffix = "." + trimmed;
        for (String fqcn : mMethods.keySet()) {
            if (fqcn.endsWith(suffix)) {
                return fqcn;
            }
        }
        return null;
    }

    private boolean isMethodInClass(@NonNull String fqcn, @NonNull String methodName) {
        Set<String> methods = mMethods.get(fqcn);
        return methods != null && methods.contains(methodName);
    }

    private boolean isMethodInAnyContext(@NonNull String methodName) {
        for (Set<String> methods : mMethods.values()) {
            if (methods.contains(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static class OnClickInfo {
        final String methodName;
        final Location location;
        final String filePath;

        OnClickInfo(String methodName, Location location, String filePath) {
            this.methodName = methodName;
            this.location = location;
            this.filePath = filePath;
        }
    }
}