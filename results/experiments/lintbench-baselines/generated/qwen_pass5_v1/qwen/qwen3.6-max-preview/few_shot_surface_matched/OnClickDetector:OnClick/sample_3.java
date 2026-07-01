package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
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

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<Location>> mReferences = new HashMap<>();
    private final Set<String> mValidMethods = new HashSet<>();

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            mReferences.computeIfAbsent(methodName, k -> new ArrayList<>())
                       .add(context.getLocation(attribute));
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.content.ContextWrapper"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (isValidOnClickMethod(method)) {
                mValidMethods.add(method.getName());
            }
        }
    }

    private boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.VOID.equals(returnType)) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
            return false;
        }
        String paramType = parameters[0].getType().getCanonicalText();
        return "android.view.View".equals(paramType);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Location>> entry : mReferences.entrySet()) {
            String methodName = entry.getKey();
            if (!mValidMethods.contains(methodName)) {
                for (Location location : entry.getValue()) {
                    context.report(ISSUE, location,
                            "Corresponding method handler '" + methodName + "(View)' not found");
                }
            }
        }
        mReferences.clear();
        mValidMethods.clear();
    }
}