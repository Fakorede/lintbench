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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global initialization required for this detector
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No cleanup required for this detector
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
        return Arrays.asList("bitmap", "adaptive-icon", "layer-list", "vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String width = element.getAttribute("android:width");
        String height = element.getAttribute("android:height");

        if (width != null && !width.isEmpty() && height != null && !height.isEmpty()) {
            if (!isValidIconSize(width) || !isValidIconSize(height)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Icon has incorrect size. Expected standard launcher icon dimensions (e.g., 48dp, 72dp, 96dp, 144dp, 192dp, or 108dp for adaptive icons).");
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Class-level scanning not required for icon size validation
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
                // Method declaration scanning not required for this check
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                String methodName = node.getMethodName();
                if (methodName != null && (methodName.contains("Icon") || methodName.contains("Size"))) {
                    List<UExpression> args = node.getValueArguments();
                    if (!args.isEmpty()) {
                        UExpression arg = args.get(0);
                        String source = arg.asSourceString();
                        if (source != null && source.matches("\\d+")) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Avoid hardcoded icon sizes in code; use dimension resources or standard launcher icon dimensions.");
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Reference scanning not required for this check
            }
        };
    }

    private static boolean isValidIconSize(String dimension) {
        if (dimension == null) {
            return false;
        }
        String val = dimension.trim();
        if (val.endsWith("dp")) {
            try {
                int size = Integer.parseInt(val.substring(0, val.length() - 2).trim());
                return size == 48 || size == 72 || size == 96 || size == 144 || size == 192 || size == 108;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}