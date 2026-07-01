package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context " +
                    "to invoke when the view is clicked. This name must correspond to a public method " +
                    "that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static class Reference {
        final XmlContext context;
        final Attr attr;
        Reference(XmlContext context, Attr attr) {
            this.context = context;
            this.attr = attr;
        }
    }

    private final Map<String, List<Reference>> onClickReferences = new HashMap<>();
    private final Set<String> validMethods = new HashSet<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Reference>> entry : onClickReferences.entrySet()) {
            String methodName = entry.getKey();
            if (!validMethods.contains(methodName)) {
                for (Reference ref : entry.getValue()) {
                    String message = "Corresponding method handler '`public void " + methodName + "(android.view.View)`' not found";
                    ref.context.report(ISSUE, ref.attr, ref.context.getLocation(ref.attr), message);
                }
            }
        }
        onClickReferences.clear();
        validMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            onClickReferences.computeIfAbsent(methodName, k -> new ArrayList<>()).add(new Reference(context, attribute));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "android.content.Context", "android.view.View", "androidx.fragment.app.Fragment");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isValidOnClick(method)) {
                validMethods.add(method.getName());
            }
        }
    }

    private boolean isValidOnClick(UMethod method) {
        if (!method.hasModifierProperty("public")) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.VOID)) {
            return false;
        }
        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 1) {
            return false;
        }
        PsiType paramType = parameters.get(0).getType();
        return paramType != null && "android.view.View".equals(paramType.getCanonicalText());
    }
}