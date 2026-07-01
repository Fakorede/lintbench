package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS, 6, Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static class PendingCheck {
        final XmlContext context;
        final Attr attribute;
        final String methodName;

        PendingCheck(XmlContext context, Attr attribute, String methodName) {
            this.context = context;
            this.attribute = attribute;
            this.methodName = methodName;
        }
    }

    private final List<PendingCheck> pendingChecks = new ArrayList<>();
    private final Set<String> validMethods = new HashSet<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            pendingChecks.add(new PendingCheck(context, attribute, methodName));
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
            public void visitMethod(UMethod node) {
                if (!node.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return;
                }

                PsiType returnType = node.getReturnType();
                if (returnType == null || !returnType.equals(PsiType.VOID)) {
                    return;
                }

                PsiParameter[] parameters = node.getParameterList().getParameters();
                if (parameters.length != 1) {
                    return;
                }

                String paramType = parameters[0].getType().getCanonicalText();
                if ("android.view.View".equals(paramType)) {
                    validMethods.add(node.getName());
                }
            }
        };
    }

    @Override
    public void afterCheckProject(Context context) {
        for (PendingCheck check : pendingChecks) {
            if (!validMethods.contains(check.methodName)) {
                check.context.report(
                        ISSUE,
                        check.attribute,
                        check.context.getLocation(check.attribute),
                        "onClick method `" + check.methodName + "` does not exist or has incorrect signature");
            }
        }
        pendingChecks.clear();
        validMethods.clear();
    }
}