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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Implementation IMPLEMENTATION =
            new Implementation(
                    OnClickDetector.class,
                    Scope.RESOURCE_AND_SOURCE_FILES_SET);

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "OnClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this "
                            + "View's context to invoke when the view is clicked. This name must "
                            + "correspond to a public method that takes exactly one parameter of "
                            + "type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<OnClickOccurrence> mOccurrences = new ArrayList<>();
    private final Set<String> mDefinedMethods = new HashSet<>();

    public OnClickDetector() {}

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (OnClickOccurrence occurrence : mOccurrences) {
            if (!mDefinedMethods.contains(occurrence.methodName)) {
                String message = String.format(
                        "Corresponding method handler 'public void %1$s(android.view.View)' not found or has invalid signature",
                        occurrence.methodName);
                context.report(ISSUE, occurrence.location, message);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value.isEmpty() || value.startsWith("@")) {
            return;
        }
        Location location = context.getLocation(attribute);
        mOccurrences.add(new OnClickOccurrence(value, location));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.content.Context");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (isValidOnClickMethod(method)) {
                mDefinedMethods.add(method.getName());
            }
        }
    }

    private boolean isValidOnClickMethod(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
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
        PsiType paramType = parameters[0].getType();
        return "android.view.View".equals(paramType.getCanonicalText());
    }

    private static class OnClickOccurrence {
        final String methodName;
        final Location location;

        OnClickOccurrence(String methodName, Location location) {
            this.methodName = methodName;
            this.location = location;
        }
    }
}