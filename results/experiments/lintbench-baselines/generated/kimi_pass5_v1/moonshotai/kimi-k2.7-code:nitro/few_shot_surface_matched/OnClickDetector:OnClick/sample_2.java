package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "Corresponding method handler not found",
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must correspond "
                            + "to a public method that takes exactly one parameter of type "
                            + "`android.view.View`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ON_CLICK = "onClick";
    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String VIEW_CLASS = "android.view.View";

    private final Map<String, List<com.android.tools.lint.detector.api.Location>>
            mPendingReferences = new HashMap<>();
    private final Set<String> mValidHandlers = new HashSet<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value != null) {
            value = value.trim();
        }
        if (value == null || value.isEmpty()) {
            return;
        }

        com.android.tools.lint.detector.api.Location location = context.getLocation(attribute);
        List<com.android.tools.lint.detector.api.Location> locations =
                mPendingReferences.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mPendingReferences.put(value, locations);
        }
        locations.add(location);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)
                    || method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (!"void".equals(method.getReturnType().getCanonicalText())) {
                continue;
            }
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }
            if (!VIEW_CLASS.equals(parameters[0].getType().getCanonicalText())) {
                continue;
            }
            mValidHandlers.add(method.getName());
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, List<com.android.tools.lint.detector.api.Location>> entry :
                mPendingReferences.entrySet()) {
            String name = entry.getKey();
            if (mValidHandlers.contains(name)) {
                continue;
            }
            String message =
                    String.format(
                            "Corresponding method handler 'public void %1$s(android.view.View)' not found",
                            name);
            for (com.android.tools.lint.detector.api.Location location : entry.getValue()) {
                context.report(ISSUE, location, message);
            }
        }
    }
}