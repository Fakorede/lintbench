package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
                            + "such as lossless encoding and transparency, require Android 4.2.1 (API 18).",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("bitmap".equals(tagName)) {
            String src = element.getAttributeNS("http://schemas.android.com/apk/res/android", "src");
            if (src != null && src.endsWith(".webp")) {
                int minSdk = context.getProject().getMinSdk();
                if (minSdk < 15) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "WebP requires API 15 (current min is " + minSdk + ")");
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(JavaContext context, UMethod method) {
        // Method-level analysis if needed
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
        // Call-level analysis if needed
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Class-level analysis if needed
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        if ("WEBP".equals(name)) {
            int minSdk = context.getProject().getMinSdk();
            if (minSdk < 15) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "WebP requires API 15 (current min is " + minSdk + ")");
            }
        } else if ("WEBP_LOSSLESS".equals(name) || "WEBP_LOSSY".equals(name)) {
            int minSdk = context.getProject().getMinSdk();
            if (minSdk < 18) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Lossless/Lossy WebP requires API 18 (current min is " + minSdk + ")");
            }
        }
    }
}