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
import com.android.tools.lint.client.api.UElementHandler;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, "
                            + "such as lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES
                    )
            );

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(Context context, Incident incident) {
        super.filterIncident(context, incident);
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        java.util.List<String> elements = new java.util.ArrayList<>();
        elements.add("ImageView");
        elements.add("ImageButton");
        return elements;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String src = element.getAttributeNS("http://schemas.android.com/apk/res/android", "src");
        if (src != null && src.contains("webp")) {
            int minSdk = context.getProject().getMinSdk();
            if (minSdk < 15) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "WebP requires Android 4.0 (API 15)"
                );
            }
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(node);
            }

            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(node);
            }
        };
    }

    public void visitMethod(UMethod node) {
    }

    public void visitCallExpression(UCallExpression node) {
    }

    public void visitClass(UClass node) {
    }

    public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public java.util.List<java.lang.Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<java.lang.Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(UClass.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }
}