package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View` and returns void.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            return;
        }

        // Data binding expressions are handled by the data binding framework.
        if (methodName.startsWith("@{") || methodName.startsWith("@=")) {
            return;
        }

        if (methodName.startsWith("@")) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    "The `onClick` attribute must be a literal method name, not a resource reference");
            return;
        }

        Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root == null) {
            return;
        }

        String contextClass = root.getAttributeNS(
                SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT);
        if (contextClass == null || contextClass.isEmpty()) {
            return;
        }

        String pkg = context.getMainProject().getPackage();
        if (contextClass.startsWith(".")) {
            if (pkg != null && !pkg.isEmpty()) {
                contextClass = pkg + contextClass;
            }
        } else if (!contextClass.contains(".")) {
            if (pkg != null && !pkg.isEmpty()) {
                contextClass = pkg + "." + contextClass;
            }
        }

        JavaEvaluator evaluator =
                context.getClient().getJavaEvaluator(context.getMainProject());
        PsiClass cls = evaluator.findClass(contextClass);
        if (cls == null) {
            return;
        }

        PsiMethod method = evaluator.findMethod(cls, methodName, 1);
        if (method == null) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("The method `%1$s(View)` does not exist in `%2$s`",
                            methodName, contextClass));
            return;
        }

        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("The onClick handler `%1$s(View)` must be public",
                            methodName));
            return;
        }

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("The onClick handler `%1$s(View)` must take exactly one `View` parameter",
                            methodName));
            return;
        }

        PsiClass viewClass = evaluator.findClass(SdkConstants.CLASS_VIEW);
        if (viewClass == null) {
            return;
        }

        PsiClass parameterClass = evaluator.getTypeClass(parameters[0].getType());
        if (parameterClass == null
                || !SdkConstants.CLASS_VIEW.equals(parameterClass.getQualifiedName())) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("The onClick handler `%1$s(View)` must take a `View` parameter",
                            methodName));
            return;
        }

        if (!PsiType.VOID.equals(method.getReturnType())) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("The onClick handler `%1$s(View)` must return void",
                            methodName));
        }
    }
}