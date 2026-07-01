package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";
    private static final String ON_CLICK = "onClick";
    private static final String TOOLS_CONTEXT = "context";
    private static final String VIEW_CLASS = "android.view.View";

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    private final Map<String, List<ClickReference>> mReferences = new HashMap<>();
    private final Map<String, Set<String>> mContexts = new HashMap<>();
    private final Map<String, Set<String>> mMethods = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ON_CLICK, TOOLS_CONTEXT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        String uri = attribute.getNamespaceURI();
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        String file = context.file.getPath();

        if (ON_CLICK.equals(name) && ANDROID_URI.equals(uri)) {
            String methodName = value.trim();
            if (methodName.isEmpty()
                    || methodName.startsWith("@")
                    || methodName.startsWith("{")
                    || methodName.startsWith("?")) {
                return;
            }
            Location location = context.getValueLocation(attribute);
            mReferences.computeIfAbsent(file, k -> new ArrayList<>())
                    .add(new ClickReference(methodName, location));
        } else if (TOOLS_CONTEXT.equals(name) && TOOLS_URI.equals(uri)) {
            Set<String> contexts = mContexts.computeIfAbsent(file, k -> new HashSet<>());
            for (String cls : value.split(",")) {
                cls = cls.trim();
                if (!cls.isEmpty()) {
                    contexts.add(cls);
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("java.lang.Object");
    }

    @Override
    public void visitClass(JavaContext context, UClass clazz) {
        String qualifiedName = clazz.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        PsiClass psiClass = clazz.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        Set<String> names = new HashSet<>();
        for (PsiMethod method : psiClass.getAllMethods()) {
            if (isValidOnClickMethod(method)) {
                names.add(method.getName());
            }
        }
        mMethods.put(qualifiedName, names);
    }

    private static boolean isValidOnClickMethod(PsiMethod method) {
        if (method.isConstructor()) {
            return false;
        }
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }
        String returnCanonical = returnType.getCanonicalText();
        if (!"void".equals(returnCanonical)
                && !"kotlin.Unit".equals(returnCanonical)
                && !"Unit".equals(returnCanonical)) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }

        PsiParameter parameter = parameterList.getParameter(0);
        if (parameter == null) {
            return false;
        }

        return isViewType(parameter.getType());
    }

    private static boolean isViewType(PsiType type) {
        String canonical = type.getCanonicalText();
        if (canonical == null) {
            return false;
        }
        if (canonical.endsWith("?") || canonical.endsWith("!")) {
            canonical = canonical.substring(0, canonical.length() - 1);
        }
        return VIEW_CLASS.equals(canonical);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mReferences.clear();
        mContexts.clear();
        mMethods.clear();
    }

    @Override
    public void afterCheckRootProject(Context context) {
        String packageName = context.getMainProject().getPackage();

        for (Map.Entry<String, List<ClickReference>> entry : mReferences.entrySet()) {
            String file = entry.getKey();
            Set<String> rawContexts = mContexts.get(file);
            if (rawContexts == null || rawContexts.isEmpty()) {
                continue;
            }

            Set<String> available = new HashSet<>();
            for (String raw : rawContexts) {
                String resolved = resolveContextClass(raw, packageName);
                if (resolved == null) {
                    continue;
                }
                Set<String> methods = mMethods.get(resolved);
                if (methods != null) {
                    available.addAll(methods);
                }
            }

            for (ClickReference ref : entry.getValue()) {
                if (!available.contains(ref.name)) {
                    String message = "The method `" + ref.name + "` is not a public method that "
                            + "takes a `View` parameter in the activity context";
                    context.report(ISSUE, ref.location, message);
                }
            }
        }
    }

    private static String resolveContextClass(String raw, String packageName) {
        String name = raw.trim();
        if (name.isEmpty()) {
            return null;
        }

        if (name.startsWith(".")) {
            if (packageName != null && !packageName.isEmpty()) {
                return packageName + name;
            } else {
                return name.substring(1);
            }
        }

        if (packageName != null && !packageName.isEmpty() && !name.contains(".")) {
            return packageName + "." + name;
        }

        return name;
    }

    private static class ClickReference {
        final String name;
        final Location location;

        ClickReference(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }
}