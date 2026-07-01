package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UastParser;
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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OnClickDetector extends Detector implements XmlScanner {

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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName.isEmpty() || methodName.startsWith("@")) {
            return;
        }

        Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root == null) {
            return;
        }

        String contextActivity = root.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT);
        if (contextActivity.isEmpty()) {
            return;
        }

        if (contextActivity.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                contextActivity = pkg + contextActivity;
            }
        }

        UastParser parser = context.getClient().getUastParser(context.getProject());
        JavaEvaluator evaluator = parser.getEvaluator();
        PsiClass psiClass = evaluator.findClass(contextActivity);
        if (psiClass == null) {
            return;
        }

        if (!hasOnClickMethod(psiClass, methodName, evaluator)) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                String.format("Corresponding method `public void %s(View)` not found in `%s`", methodName, contextActivity)
            );
        }
    }

    private boolean hasOnClickMethod(PsiClass psiClass, String methodName, JavaEvaluator evaluator) {
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        for (PsiMethod method : methods) {
            if (evaluator.isPublic(method)) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameters.length == 1) {
                    PsiType paramType = parameters[0].getType();
                    if (paramType.getCanonicalText().equals("android.view.View")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}