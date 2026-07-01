package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES);

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\;' to escape characters such as '\\n' or " +
            "'\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    private static class PendingAttribute {
        final XmlContext context;
        final Attr attribute;
        PendingAttribute(XmlContext context, Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }

    private final Map<String, List<PendingAttribute>> mPendingAttributes = new HashMap<>();
    private final Set<String> mValidMethods = new HashSet<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<PendingAttribute>> entry : mPendingAttributes.entrySet()) {
            if (!mValidMethods.contains(entry.getKey())) {
                for (PendingAttribute pending : entry.getValue()) {
                    pending.context.report(ISSUE, pending.attribute,
                            pending.context.getLocation(pending.attribute),
                            "Corresponding method handler '" + entry.getKey() + "(android.view.View)' not found");
                }
            }
        }
        mPendingAttributes.clear();
        mValidMethods.clear();
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            mPendingAttributes.computeIfAbsent(methodName, k -> new ArrayList<>())
                    .add(new PendingAttribute(context, attribute));
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "android.view.ContextThemeWrapper");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            String name = method.getName();
            if (!mPendingAttributes.containsKey(name)) {
                continue;
            }
            if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
                    PsiType.VOID.equals(method.getReturnType()) &&
                    method.getParameterList().getParametersCount() == 1) {
                PsiParameter param = method.getParameterList().getParameters()[0];
                if ("android.view.View".equals(param.getType().getCanonicalText())) {
                    mValidMethods.add(name);
                }
            }
        }
    }
}