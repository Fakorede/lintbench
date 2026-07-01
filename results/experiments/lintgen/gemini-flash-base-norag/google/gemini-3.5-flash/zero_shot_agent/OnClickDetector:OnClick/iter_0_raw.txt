package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String methodName = attribute.getValue();
        if (methodName.isEmpty() || methodName.startsWith("@") || methodName.startsWith("{")) {
            return;
        }

        Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root == null) {
            return;
        }

        String toolsContext = root.getAttributeNS(SdkConstants.TOOLS_URI, "context");
        if (toolsContext.isEmpty()) {
            return;
        }

        if (toolsContext.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                toolsContext = pkg + toolsContext;
            }
        } else if (!toolsContext.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                toolsContext = pkg + "." + toolsContext;
            }
        }

        PsiClass psiClass = context.getEvaluator().findClass(toolsContext);
        if (psiClass == null) {
            return;
        }

        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        boolean found = false;
        for (PsiMethod method : methods) {
            if (isValidOnClickMethod(method)) {
                found = true;
                break;
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Corresponding method `public void %s(View)` not found in `%s`", methodName, toolsContext)
            );
        }
    }

    private boolean isValidOnClickMethod(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        return type.getCanonicalText().equals("android.view.View");
    }
}