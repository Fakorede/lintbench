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
import com.intellij.psi.PsiMethod;
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
                    + "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0).",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 18) {
            return;
        }
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attributes.item(i);
            String value = attr.getValue();
            if (value != null && value.endsWith(".webp")) {
                if (minSdk < 15) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "WebP requires API 15. Current minSdk is " + minSdk + ".");
                } else {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "WebP transparency and lossless encoding require API 18. Current minSdk is " + minSdk + ".");
                }
            }
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(JavaContext context, UMethod node) {
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }
}