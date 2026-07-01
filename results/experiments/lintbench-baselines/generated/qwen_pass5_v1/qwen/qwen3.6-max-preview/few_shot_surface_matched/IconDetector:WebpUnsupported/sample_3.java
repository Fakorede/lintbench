package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WebpUnsupported",
            "WebP Unsupported",
            "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                    + "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public boolean filterIncident(@com.android.annotations.NonNull Incident incident) {
        return incident.issue == ISSUE;
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull Scope scope) {
        return scope == Scope.JAVA_FILE_SCOPE || scope == Scope.RESOURCE_FILE_SCOPE;
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String src = element.getAttribute("android:src");
        if (src != null && src.endsWith(".webp")) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk < 15) {
                context.report(ISSUE, element, context.getLocation(element), "WebP requires API 15");
            } else if (minSdk < 18) {
                context.report(ISSUE, element, context.getLocation(element), "Lossless/transparent WebP requires API 18");
            }
        }
    }

    @com.android.annotations.Nullable
    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UMethod method) {
    }

    @Override
    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName != null && methodName.contains("Webp")) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk < 15) {
                context.report(ISSUE, node, context.getLocation(node), "WebP requires API 15");
            }
        }
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if (name != null && name.toLowerCase().endsWith("_webp")) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk < 15) {
                context.report(ISSUE, node, context.getLocation(node), "WebP requires API 15");
            }
        }
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.singletonList(UClass.class);
    }
}