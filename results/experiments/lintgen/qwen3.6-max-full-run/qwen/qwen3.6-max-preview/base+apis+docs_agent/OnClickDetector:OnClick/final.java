package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class OnClickDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or '\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            return;
        }

        Element root = context.document.getDocumentElement();
        if (root == null) {
            return;
        }

        String contextClass = root.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT);
        if (contextClass == null || contextClass.isEmpty()) {
            return;
        }

        contextClass = contextClass.trim();
        String fqn = contextClass;
        if (contextClass.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                fqn = pkg + contextClass;
            } else {
                return;
            }
        }

        PsiClass psiClass = context.getClient().findClass(fqn);
        if (psiClass == null) {
            return;
        }

        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        for (PsiMethod method : methods) {
            PsiType returnType = method.getReturnType();
            if (returnType != null && returnType.equals(PsiType.VOID) &&
                method.hasModifierProperty(PsiModifier.PUBLIC) &&
                method.getParameterList().getParametersCount() == 1) {
                PsiParameter param = method.getParameterList().getParameters()[0];
                PsiType paramType = param.getType();
                if (paramType != null && "android.view.View".equals(paramType.getCanonicalText())) {
                    return;
                }
            }
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                "Corresponding method handler '`public void " + methodName + "(android.view.View)`' not found");
    }
}