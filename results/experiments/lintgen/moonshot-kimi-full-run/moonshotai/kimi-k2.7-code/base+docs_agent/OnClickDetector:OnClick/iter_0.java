package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OnClickDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
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

        String className = resolveContextClass(context, attribute.getOwnerElement());
        if (className == null) {
            return;
        }

        JavaEvaluator evaluator = context.getClient().getEvaluator();
        if (evaluator == null) {
            return;
        }

        PsiClass cls = evaluator.findClass(className);
        if (cls == null) {
            return;
        }

        if (!hasMatchingMethod(cls, methodName, evaluator)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    String.format(
                            "Method '%s' is not a public method with a `View` parameter in '%s'",
                            methodName,
                            className
                    )
            );
        }
    }

    private static String resolveContextClass(XmlContext context, Element element) {
        Node node = element;
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String contextClass = el.getAttributeNS(SdkConstants.TOOLS_URI, "context");
                if (contextClass != null && !contextClass.isEmpty()) {
                    if (contextClass.startsWith(".")) {
                        String pkg = context.getProject().getPackage();
                        if (pkg != null) {
                            return pkg + contextClass;
                        }
                    }
                    return contextClass;
                }
            }
            node = node.getParentNode();
        }
        return null;
    }

    private static boolean hasMatchingMethod(PsiClass cls, String methodName, JavaEvaluator evaluator) {
        for (PsiMethod method : cls.findMethodsByName(methodName, true)) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }
            PsiParameterList params = method.getParameterList();
            if (params.getParametersCount() != 1) {
                continue;
            }
            PsiParameter param = params.getParameters()[0];
            PsiType type = param.getType();
            PsiClass paramClass = PsiUtil.resolveClassInType(type);
            if (paramClass != null
                    && evaluator.extendsClass(paramClass, SdkConstants.CLASS_VIEW, false)) {
                return true;
            }
        }
        return false;
    }
}