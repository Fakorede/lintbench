package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OnClickDetector.class,
                    Scope.JAVA_AND_RESOURCE_FILES);

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "onClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this "
                            + "View's context to invoke when the view is clicked. This name must "
                            + "correspond to a public method that takes exactly one parameter "
                            + "of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, List<Location>> mOnClickMethods = new HashMap<>();
    private final Set<String> mDeclaredMethods = new HashSet<>();

    public OnClickDetector() {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue().trim();
        if (value.isEmpty() || value.startsWith("@")) {
            return;
        }
        Location location = context.getLocation(attribute);
        List<Location> locations = mOnClickMethods.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mOnClickMethods.put(value, locations);
        }
        locations.add(location);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty("public") && !method.hasModifierProperty("static")) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameters.length == 1) {
                    PsiType type = parameters[0].getType();
                    String typeName = type.getCanonicalText();
                    if ("android.view.View".equals(typeName) || "View".equals(type.getPresentableText())) {
                        mDeclaredMethods.add(method.getName());
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, List<Location>> entry : mOnClickMethods.entrySet()) {
            String methodName = entry.getKey();
            if (!mDeclaredMethods.contains(methodName)) {
                List<Location> locations = entry.getValue();
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            String.format("Corresponding method handler 'public void %s(View)' not found", methodName)
                    );
                }
            }
        }
    }
}