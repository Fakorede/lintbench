package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global initialization required
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No global cleanup required
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int minSdk = getMinSdk(context);
        if (minSdk >= 18) {
            return;
        }
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String value = attr.getNodeValue();
            if (value != null && value.contains(".webp")) {
                String message = minSdk < 15
                        ? "WebP requires API 15+ (current minSdk is " + minSdk + ")"
                        : "Lossless/transparent WebP requires API 18+ (current minSdk is " + minSdk + ")";
                context.report(ISSUE, element, context.getLocation(attr), message);
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not applicable for this check
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not applicable for this check
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                int minSdk = getMinSdk(context);
                if (minSdk >= 18) {
                    return;
                }
                for (UExpression arg : node.getValueArguments()) {
                    if (arg instanceof ULiteralExpression) {
                        Object val = ((ULiteralExpression) arg).getValue();
                        if (val instanceof String && ((String) val).contains(".webp")) {
                            String message = minSdk < 15
                                    ? "WebP requires API 15+ (current minSdk is " + minSdk + ")"
                                    : "Lossless/transparent WebP requires API 18+ (current minSdk is " + minSdk + ")";
                            context.report(ISSUE, arg, context.getLocation(arg), message);
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not applicable for this check
            }
        };
    }

    private int getMinSdk(@NonNull Context context) {
        try {
            return context.getMainProject().getMinSdkVersion().getApiLevel();
        } catch (Exception e) {
            return 1;
        }
    }
}