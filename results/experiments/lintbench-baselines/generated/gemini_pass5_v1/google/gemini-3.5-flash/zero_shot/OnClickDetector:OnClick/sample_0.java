package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }

        String methodName = attribute.getValue();
        if (methodName.isEmpty() || methodName.startsWith("@") || methodName.startsWith("?")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String contextActivity = getContextActivity(element);
        if (contextActivity == null || contextActivity.isEmpty()) {
            return;
        }

        String fqcn = contextActivity;
        if (fqcn.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqcn = pkg + fqcn;
            }
        } else if (!fqcn.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                fqcn = pkg + "." + fqcn;
            }
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator == null) {
            return;
        }

        PsiClass psiClass = evaluator.findClass(fqcn);
        if (psiClass == null) {
            return;
        }

        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        boolean found = false;
        for (PsiMethod method : methods) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                PsiParameterList parameterList = method.getParameterList();
                if (parameterList.getParametersCount() == 1) {
                    PsiParameter parameter = parameterList.getParameters()[0];
                    PsiType type = parameter.getType();
                    if (type.getCanonicalText().equals("android.view.View")) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (!found) {
            String message = String.format(
                    "Corresponding method `public void %1$s(android.view.View)` not found in `%2$s`",
                    methodName, fqcn);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private String getContextActivity(Element element) {
        Element current = element;
        while (current != null) {
            String contextAttr = current.getAttributeNS("http://schemas.android.com/tools", "context");
            if (contextAttr != null && !contextAttr.isEmpty()) {
                return contextAttr;
            }
            Node parent = current.getParentNode();
            if (parent instanceof Element) {
                current = (Element) parent;
            } else {
                break;
            }
        }
        return null;
    }
}