package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaEvaluator;
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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OnClickDetector extends Detector implements XmlScanner {

    private static final String VIEW_CLASS = "android.view.View";

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this "
                    + "View's context to invoke when the view is clicked. This name must "
                    + "correspond to a public method that takes exactly one parameter of "
                    + "type `View`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.startsWith("@") || value.startsWith("{")) {
            return;
        }

        String methodName = value;

        String className = getContextClassName(context, attribute);
        if (className == null) {
            return;
        }

        JavaEvaluator evaluator = context.getDriver().getJavaEvaluator();
        PsiClass cls = evaluator.findClass(className);
        if (cls == null) {
            return;
        }

        if (!hasOnClickMethod(cls, methodName)) {
            String message = String.format(
                    "Method '%1$s' is not found in `%2$s`",
                    methodName, cls.getQualifiedName());
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    private String getContextClassName(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        while (element.getParentNode() instanceof Element) {
            element = (Element) element.getParentNode();
        }

        String contextValue = element.getAttributeNS(TOOLS_URI, ATTR_CONTEXT);
        if (contextValue == null || contextValue.isEmpty()) {
            return null;
        }

        String packageName = context.getMainProject().getPackage();
        if (packageName == null || packageName.isEmpty()) {
            return contextValue;
        }

        if (contextValue.startsWith(".")) {
            return packageName + contextValue;
        } else if (!contextValue.contains(".")) {
            return packageName + "." + contextValue;
        } else {
            return contextValue;
        }
    }

    private boolean hasOnClickMethod(@NonNull PsiClass cls, @NonNull String methodName) {
        for (PsiMethod method : cls.findMethodsByName(methodName, true)) {
            if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            if (method.getParameterList().getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = method.getParameterList().getParameters()[0];
            PsiType type = parameter.getType();
            if (type == null) {
                continue;
            }

            if (VIEW_CLASS.equals(type.getCanonicalText())) {
                return true;
            }
        }

        return false;
    }
}