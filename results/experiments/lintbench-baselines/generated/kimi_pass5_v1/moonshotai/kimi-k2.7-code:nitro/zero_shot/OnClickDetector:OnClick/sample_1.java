package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        if (value.startsWith("@") || value.startsWith("?")) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    "The onClick attribute value must be a string, not a resource reference");
            return;
        }

        if (!isValidJavaIdentifier(value)) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("'%1$s' is not a valid Java method name", value));
            return;
        }

        String methodName = value;
        String contextClass = getContextClass(context);
        if (contextClass == null) {
            return;
        }

        JavaEvaluator evaluator = context.getClient().getJavaEvaluator();
        if (evaluator == null) {
            return;
        }

        if (!hasClickHandler(evaluator, contextClass, methodName)) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    String.format("Corresponding method handler '%1$s(View)' not found", methodName));
        }
    }

    @Nullable
    private static String getContextClass(@NotNull XmlContext context) {
        Element root = context.document.getDocumentElement();
        if (root == null) {
            return null;
        }

        String contextAttr = root.getAttributeNS(SdkConstants.TOOLS_URI, "context");
        if (contextAttr == null || contextAttr.isEmpty()) {
            return null;
        }

        if (contextAttr.startsWith(".")) {
            String pkg = context.getMainProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                return pkg + contextAttr;
            }
            return contextAttr.substring(1);
        }

        return contextAttr;
    }

    private static boolean hasClickHandler(@NotNull JavaEvaluator evaluator,
                                           @NotNull String className,
                                           @NotNull String methodName) {
        PsiClass cls = evaluator.findClass(className);
        if (cls == null) {
            return false;
        }

        for (PsiMethod method : cls.findMethodsByName(methodName, true)) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = parameterList.getParameters()[0];
            PsiType type = parameter.getType();
            if ("android.view.View".equals(type.getCanonicalText())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isValidJavaIdentifier(@NotNull String name) {
        if (name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1, n = name.length(); i < n; i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}