package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP format requires API 15+, transparency/lossless requires API 18+",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                            + "encoding and transparency, requires Android 4.2.1 (API 18).",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization hook before analyzing the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Cleanup hook after analyzing each module/project
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "ImageButton", "bitmap", "item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value != null && value.toLowerCase().endsWith(".webp")) {
                checkWebpUsage(context, attr, value);
            }
        }
    }

    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        checkNodeForWebp(context, node);
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        checkNodeForWebp(context, node);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        checkNodeForWebp(context, node);
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        checkNodeForWebp(context, node);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    private void checkWebpUsage(@NonNull XmlContext context, @NonNull Attr attribute, @NonNull String value) {
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk < 15) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "WebP requires Android 4.0 (API 15) or higher");
        } else if (minSdk < 18) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "WebP transparency and lossless encoding require Android 4.2.1 (API 18) or higher");
        }
    }

    private void checkNodeForWebp(@NonNull JavaContext context, @NonNull UElement node) {
        String source = node.asSourceString();
        if (source != null && source.toLowerCase().contains(".webp")) {
            int minSdk = context.getMainProject().getMinSdk();
            if (minSdk < 15) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "WebP requires Android 4.0 (API 15) or higher");
            } else if (minSdk < 18) {
                context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "WebP transparency and lossless encoding require Android 4.2.1 (API 18) or higher");
            }
        }
    }
}