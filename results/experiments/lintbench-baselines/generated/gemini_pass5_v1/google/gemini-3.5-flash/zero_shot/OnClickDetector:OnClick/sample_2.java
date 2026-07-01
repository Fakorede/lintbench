package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this " +
            "View's context to invoke when the view is clicked. This name must " +
            "correspond to a public method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILES, Scope.JAVA_FILES)
            )
    );

    private final List<OnClickOccurrence> occurrences = new ArrayList<>();
    private final Map<String, Set<String>> classToMethods = new HashMap<>();
    private final Set<String> allOnClickMethods = new HashSet<>();

    private static class OnClickOccurrence {
        final String methodName;
        final String contextClass;
        final Location location;

        OnClickOccurrence(String methodName, String contextClass, Location location) {
            this.methodName = methodName;
            this.contextClass = contextClass;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        occurrences.clear();
        classToMethods.clear();
        allOnClickMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty() || methodName.contains("{") || methodName.contains("}")) {
            return;
        }
        Element root = attribute.getOwnerDocument().getDocumentElement();
        String contextAttr = root != null ? root.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT) : null;
        if (contextAttr == null || contextAttr.isEmpty()) {
            contextAttr = root != null ? root.getAttribute("tools:context") : null;
        }
        String pkg = context.getProject().getPackage();
        String contextClass = getFullyQualifiedClassName(contextAttr, pkg);
        Location location = context.getNameLocation(attribute);
        occurrences.add(new OnClickOccurrence(methodName, contextClass, location));
    }

    private String getFullyQualifiedClassName(String contextAttr, String pkg) {
        if (contextAttr == null || contextAttr.isEmpty()) {
            return null;
        }
        if (contextAttr.startsWith(".")) {
            return pkg != null ? pkg + contextAttr : contextAttr;
        } else if (contextAttr.indexOf('.') == -1) {
            return pkg != null ? pkg + "." + contextAttr : contextAttr;
        } else {
            return contextAttr;
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod method) {
                if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return;
                }
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() != 1) {
                    return;
                }
                UParameter parameter = parameters.get(0);
                PsiType type = parameter.getType();
                if (type == null || !type.getCanonicalText().equals("android.view.View")) {
                    return;
                }

                String methodName = method.getName();
                UClass containingClass = UastUtils.getContainingUClass(method);
                if (containingClass != null) {
                    String qualifiedName = containingClass.getQualifiedName();
                    if (qualifiedName != null) {
                        classToMethods.computeIfAbsent(qualifiedName, k -> new HashSet<>()).add(methodName);
                    }
                }
                allOnClickMethods.add(methodName);
            }
        };
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (OnClickOccurrence occurrence : occurrences) {
            if (occurrence.contextClass != null) {
                if (classToMethods.containsKey(occurrence.contextClass)) {
                    Set<String> methods = classToMethods.get(occurrence.contextClass);
                    if (methods == null || !methods.contains(occurrence.methodName)) {
                        report(context, occurrence);
                    }
                } else {
                    if (!context.getDriver().isIncremental() && !classToMethods.isEmpty() && !allOnClickMethods.contains(occurrence.methodName)) {
                        report(context, occurrence);
                    }
                }
            } else {
                if (!context.getDriver().isIncremental() && !classToMethods.isEmpty() && !allOnClickMethods.contains(occurrence.methodName)) {
                    report(context, occurrence);
                }
            }
        }
    }

    private void report(Context context, OnClickOccurrence occurrence) {
        String message = String.format("Corresponding method `public void %1$s(View)` not found", occurrence.methodName);
        context.report(ISSUE, occurrence.location, message);
    }
}