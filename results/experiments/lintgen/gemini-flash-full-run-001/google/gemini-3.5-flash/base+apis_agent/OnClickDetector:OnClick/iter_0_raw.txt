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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OnClickDetector extends Detector implements XmlScanner {

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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty() || methodName.startsWith("@") || methodName.startsWith("{") || methodName.contains("$")) {
            return;
        }

        Document doc = attribute.getOwnerDocument();
        if (doc == null) {
            return;
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return;
        }

        String contextClass = root.getAttributeNS(SdkConstants.TOOLS_URI, "context");
        if (contextClass == null || contextClass.isEmpty()) {
            return;
        }

        if (contextClass.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                contextClass = pkg + contextClass;
            }
        } else if (!contextClass.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                contextClass = pkg + "." + contextClass;
            }
        }

        UastParser parser = context.getClient().getUastParser(context.getProject());
        JavaEvaluator evaluator = parser.getEvaluator();
        PsiClass psiClass = evaluator.findClass(contextClass);
        if (psiClass == null) {
            return;
        }

        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        if (methods.length == 0) {
            String message = String.format("Method '%1$s' does not exist in '%2$s'", methodName, contextClass);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
            return;
        }

        boolean found = false;
        for (PsiMethod method : methods) {
            if (isValidOnClickMethod(method)) {
                found = true;
                break;
            }
        }

        if (!found) {
            String message = String.format("Method '%1$s' should be public, return void, and take a single View parameter", methodName);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private boolean isValidOnClickMethod(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        if (!PsiType.VOID.equals(method.getReturnType())) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType) {
            PsiClass cls = ((PsiClassType) type).resolve();
            if (cls != null && "android.view.View".equals(cls.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}