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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
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

    private static final List<String> EXPECTED_LAUNCHER_SIZES_DP = Arrays.asList(
            "48dp", "72dp", "96dp", "144dp", "192dp", "432dp");

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No global state initialization required for this detector
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No cleanup required
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
        return Arrays.asList("bitmap", "adaptive-icon", "icon", "vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element != element.getOwnerDocument().getDocumentElement()) {
            return;
        }

        String fileName = context.file.getName();
        boolean isLauncherIcon = fileName.contains("ic_launcher")
                || context.getResourceFolderType() == ResourceFolderType.MIPMAP;

        if (!isLauncherIcon) {
            return;
        }

        String width = element.getAttribute("android:width");
        String height = element.getAttribute("android:height");

        if (!width.isEmpty() && !EXPECTED_LAUNCHER_SIZES_DP.contains(width)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Launcher icon width '" + width + "' does not match expected standard sizes: " + EXPECTED_LAUNCHER_SIZES_DP);
        }
        if (!height.isEmpty() && !EXPECTED_LAUNCHER_SIZES_DP.contains(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Launcher icon height '" + height + "' does not match expected standard sizes: " + EXPECTED_LAUNCHER_SIZES_DP);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Class-level scanning not required; handled via UAST handler
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
                if (methodName == null) {
                    return;
                }
                if (methodName.contains("Icon") || methodName.contains("icon")) {
                    for (UExpression arg : node.getValueArguments()) {
                        String src = arg.asSourceString();
                        if (src != null && src.matches("\\d+")) {
                            try {
                                int val = Integer.parseInt(src);
                                if (val > 0 && val < 48) {
                                    context.report(
                                            ISSUE,
                                            node,
                                            context.getLocation(node),
                                            "Icon size argument (" + val + ") is smaller than the minimum recommended launcher icon size (48dp).");
                                    break;
                                }
                            } catch (NumberFormatException ignored) {
                                // Ignore non-numeric parse failures
                            }
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String identifier = node.getIdentifier();
                if (identifier != null && identifier.toUpperCase().contains("ICON_SIZE")) {
                    // Reference to icon size constants detected.
                    // Detailed constant value resolution would require type evaluation,
                    // which is omitted here for brevity and performance.
                }
            }
        };
    }
}