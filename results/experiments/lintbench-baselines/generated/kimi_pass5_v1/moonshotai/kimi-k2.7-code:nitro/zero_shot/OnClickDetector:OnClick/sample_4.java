package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`. The value must be a "
                    + "string (not a resource reference); Java escape sequences are allowed but the "
                    + "resulting name must identify an existing method.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String VIEW_SIGNATURE = "Landroid/view/View;";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute cannot be empty");
            return;
        }

        if (value.startsWith("@") || value.startsWith("?")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute must be a method name, not a resource reference");
            return;
        }

        Element root = attribute.getOwnerElement().getOwnerDocument().getDocumentElement();
        String contextClass = root.getAttributeNS(
                SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT);
        if (contextClass == null || contextClass.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String fqcn = getFqcn(packageName, contextClass);

        JavaParser parser = context.getClient().getJavaParser();
        if (parser == null) {
            return;
        }

        ResolvedClass resolved = parser.findClass(context.getProject(), fqcn);
        if (resolved == null) {
            return;
        }

        if (!hasMatchingOnClickMethod(resolved, value)) {
            String message = String.format(
                    "Corresponding public method '%1$s(android.view.View)' not found in class %2$s",
                    value, fqcn);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean hasMatchingOnClickMethod(
            @NonNull ResolvedClass cls, @NonNull String methodName) {
        for (ResolvedMethod method : cls.getMethods(true)) {
            if (!methodName.equals(method.getName()) || !method.isPublic()) {
                continue;
            }
            if (method.getArgumentCount() != 1) {
                continue;
            }
            ResolvedClass argType = method.getArgumentType(0);
            if (argType != null && VIEW_SIGNATURE.equals(argType.getSignature())) {
                return true;
            }
        }
        return false;
    }

    private static String getFqcn(@Nullable String packageName, @NonNull String className) {
        if (className.startsWith(".")) {
            return (packageName != null ? packageName : "") + className;
        } else if (className.indexOf('.') != -1) {
            return className;
        } else if (packageName != null && !packageName.isEmpty()) {
            return packageName + "." + className;
        } else {
            return className;
        }
    }
}