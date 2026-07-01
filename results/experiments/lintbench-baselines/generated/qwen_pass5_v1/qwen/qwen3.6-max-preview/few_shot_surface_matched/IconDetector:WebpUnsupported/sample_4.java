package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Scope scope) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Node attr = attributes.item(i);
            String value = attr.getNodeValue();
            if (value != null && value.endsWith(".webp")) {
                int minSdk = context.getMainProject().getMinSdk();
                if (minSdk < 15) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "WebP requires API level 15 (current minSdk is " + minSdk + ")");
                } else if (minSdk < 18) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "WebP lossless encoding and transparency require API level 18 (current minSdk is " + minSdk + ")");
                }
            }
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override public void visitMethod(UMethod node) {}
            @Override public void visitCallExpression(UCallExpression node) {}
            @Override public void visitClass(UClass node) {}
            @Override public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {}
        };
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        // Extension point for method scanning
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression call) {
        for (UExpression arg : call.getValueArguments()) {
            String text = arg.asSourceString();
            if (text != null && text.endsWith(".webp")) {
                int minSdk = context.getMainProject().getMinSdk();
                if (minSdk < 15) {
                    context.report(ISSUE, call, context.getLocation(call),
                            "WebP requires API level 15 (current minSdk is " + minSdk + ")");
                } else if (minSdk < 18) {
                    context.report(ISSUE, call, context.getLocation(call),
                            "WebP lossless encoding and transparency require API level 18 (current minSdk is " + minSdk + ")");
                }
            }
        }
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Extension point for class scanning
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if (name != null && name.toLowerCase().endsWith("_webp")) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk < 15) {
                context.report(ISSUE, node, context.getLocation(node),
                        "WebP requires API level 15 (current minSdk is " + minSdk + ")");
            } else if (minSdk < 18) {
                context.report(ISSUE, node, context.getLocation(node),
                        "WebP lossless encoding and transparency require API level 18 (current minSdk is " + minSdk + ")");
            }
        }
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class, UClass.class, UMethod.class);
    }
}