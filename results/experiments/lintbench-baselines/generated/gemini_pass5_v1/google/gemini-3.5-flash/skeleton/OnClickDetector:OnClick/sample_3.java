package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
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
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context "
                            + "to invoke when the view is clicked. This name must correspond to a public method "
                            + "that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, List<Location>> mOnClickMethods = new HashMap<>();
    private final Set<String> mDeclaredMethods = new HashSet<>();
    private boolean mSeenActivity = false;

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mSeenActivity) {
            return;
        }
        for (Map.Entry<String, List<Location>> entry : mOnClickMethods.entrySet()) {
            String methodName = entry.getKey();
            if (!mDeclaredMethods.contains(methodName)) {
                List<Location> locations = entry.getValue();
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            String.format("Corresponding method `public void %s(android.view.View)` not found", methodName));
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if ("http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            String value = attribute.getValue();
            if (value != null && !value.isEmpty() && !value.startsWith("@")) {
                List<Location> locations = mOnClickMethods.computeIfAbsent(value, k -> new ArrayList<>());
                locations.add(context.getLocation(attribute));
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mSeenActivity = true;
        for (UMethod method : declaration.getMethods()) {
            PsiMethod psiMethod = method.getJavaPsi();
            if (psiMethod != null) {
                if (context.getEvaluator().isPublic(psiMethod) && !context.getEvaluator().isStatic(psiMethod)) {
                    PsiParameterList parameterList = psiMethod.getParameterList();
                    if (parameterList.getParametersCount() == 1) {
                        PsiParameter parameter = parameterList.getParameters()[0];
                        PsiType type = parameter.getType();
                        if (type.getCanonicalText().equals("android.view.View")) {
                            mDeclaredMethods.add(psiMethod.getName());
                        }
                    }
                }
            }
        }
    }
}