package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VIEW_CLASS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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
import com.intellij.psi.util.PsiTypesUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            OnClickDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`. Must be a string "
                    + "value, using '\\;' to escape characters such as '\\n' or '\\uxxxx' for a "
                    + "unicode character.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo(
                    "https://developer.android.com/reference/android/view/View.html#attr_android:onClick");

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Data binding expressions are handled by the data binding framework, not by
        // android:onClick reflection.
        if (value.startsWith("@{") || value.startsWith("@={")) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass handlerClass = getHandlerClass(context, attribute, evaluator);
        if (handlerClass == null) {
            return;
        }

        PsiMethod[] methods = handlerClass.findMethodsByName(value, true);
        if (methods.length == 0) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("Corresponding method handler '%1$s' not found", value));
            return;
        }

        for (PsiMethod method : methods) {
            if (isValidOnClickMethod(method)) {
                return;
            }
        }

        context.report(ISSUE, attribute, context.getLocation(attribute),
                String.format(
                        "Corresponding method handler '%1$s' must be 'public void %1$s(View)'",
                        value));
    }

    private static boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }

        PsiParameter parameter = parameterList.getParameters()[0];
        PsiClass parameterClass = PsiTypesUtil.getPsiClass(parameter.getType());
        return parameterClass != null && VIEW_CLASS.equals(parameterClass.getQualifiedName());
    }

    @Nullable
    private static PsiClass getHandlerClass(@NonNull XmlContext context, @NonNull Attr attribute,
            @NonNull JavaEvaluator evaluator) {
        String className = getContextClassName(attribute);
        if (className == null || className.isEmpty()) {
            return null;
        }

        String packageName = context.getProject().getPackage();
        if (className.startsWith(".")) {
            if (packageName == null || packageName.isEmpty()) {
                return null;
            }
            className = packageName + className;
        } else if (!className.contains(".")) {
            if (packageName == null || packageName.isEmpty()) {
                return null;
            }
            className = packageName + "." + className;
        }

        return evaluator.findClass(className);
    }

    @Nullable
    private static String getContextClassName(@NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return null;
        }

        Node root = element;
        Node parent;
        while ((parent = root.getParentNode()) != null
                && parent.getNodeType() == Node.ELEMENT_NODE) {
            root = parent;
        }

        if (root.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        String context = ((Element) root).getAttributeNS(TOOLS_URI, "context");
        return (context == null || context.isEmpty()) ? null : context;
    }
}