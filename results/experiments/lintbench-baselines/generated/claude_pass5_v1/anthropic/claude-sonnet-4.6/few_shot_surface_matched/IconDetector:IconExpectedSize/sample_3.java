package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE);

    public static final Issue ICON_EXPECTED_SIZE =
            Issue.create(
                            "IconExpectedSize",
                            "Icon has incorrect size",
                            "There are predefined sizes (for each density) for launcher icons. "
                                    + "You should follow these conventions to make sure your icons "
                                    + "fit in with the overall look of the platform.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    // Expected launcher icon sizes by density
    private static final int MDPI_SIZE = 48;
    private static final int HDPI_SIZE = 72;
    private static final int XHDPI_SIZE = 96;
    private static final int XXHDPI_SIZE = 144;
    private static final int XXXHDPI_SIZE = 192;

    private static final String ATTR_ICON = "icon";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_RECEIVER = "receiver";
    private static final String TAG_PROVIDER = "provider";

    public IconDetector() {}

    // -----------------------------------------------------------------------
    // Detector lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Reset any project-level state before checking starts
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Finalize any per-project state after checking completes
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Scope scope) {
        return scope == Scope.JAVA_FILE || scope == Scope.MANIFEST;
    }

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                TAG_SERVICE,
                TAG_RECEIVER,
                TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String iconValue = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_ICON);
        if (iconValue == null || iconValue.isEmpty()) {
            // Try without namespace
            iconValue = element.getAttribute("android:icon");
        }
        if (iconValue == null || iconValue.isEmpty()) {
            return;
        }

        // Check if the icon reference looks like a launcher icon
        if (!iconValue.startsWith("@mipmap/") && !iconValue.startsWith("@drawable/")) {
            return;
        }

        // Report a warning about verifying icon sizes follow density conventions
        String tagName = element.getTagName();
        if (TAG_APPLICATION.equals(tagName)) {
            context.report(
                    ICON_EXPECTED_SIZE,
                    element,
                    context.getElementLocation(element),
                    "Launcher icons should follow the size conventions: "
                            + MDPI_SIZE + "x" + MDPI_SIZE + " (mdpi), "
                            + HDPI_SIZE + "x" + HDPI_SIZE + " (hdpi), "
                            + XHDPI_SIZE + "x" + XHDPI_SIZE + " (xhdpi), "
                            + XXHDPI_SIZE + "x" + XXHDPI_SIZE + " (xxhdpi), "
                            + XXXHDPI_SIZE + "x" + XXXHDPI_SIZE + " (xxxhdpi)");
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public org.jetbrains.uast.visitor.UastVisitor createUastHandler(@NonNull JavaContext context) {
        return new IconUsageVisitor(context);
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "setImageResource",
                "setImageDrawable",
                "setIcon");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression node,
            @NonNull PsiMethod method) {
        // Check calls to icon-setting methods for size compliance
        String methodName = method.getName();
        if ("setIcon".equals(methodName)
                || "setImageResource".equals(methodName)
                || "setImageDrawable".equals(methodName)) {
            // Report potential size issue for dynamically set icons
            context.report(
                    ICON_EXPECTED_SIZE,
                    node,
                    context.getCallLocation(node, true, false),
                    "Ensure that icons set programmatically follow the density-specific "
                            + "size conventions for launcher icons.");
        }
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Handled via visitMethod above
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level checks needed for icon size
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        String name = node.getIdentifier();
        // Look for references to icon resources by name convention
        if (name != null
                && (name.startsWith("ic_launcher") || name.startsWith("icon_"))) {
            context.report(
                    ICON_EXPECTED_SIZE,
                    node,
                    context.getLocation(node),
                    "Ensure icon `" + name + "` follows the density-specific "
                            + "size conventions: "
                            + MDPI_SIZE + "x" + MDPI_SIZE + " (mdpi), "
                            + HDPI_SIZE + "x" + HDPI_SIZE + " (hdpi), "
                            + XHDPI_SIZE + "x" + XHDPI_SIZE + " (xhdpi), "
                            + XXHDPI_SIZE + "x" + XXHDPI_SIZE + " (xxhdpi), "
                            + XXXHDPI_SIZE + "x" + XXXHDPI_SIZE + " (xxxhdpi).");
        }
    }

    // -----------------------------------------------------------------------
    // Inner visitor
    // -----------------------------------------------------------------------

    private static class IconUsageVisitor extends AbstractUastVisitor {

        private final JavaContext mContext;

        IconUsageVisitor(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            String methodName = node.getMethodName();
            if (methodName == null) {
                return super.visitCallExpression(node);
            }
            if ("setIcon".equals(methodName)
                    || "setImageResource".equals(methodName)
                    || "setImageDrawable".equals(methodName)) {
                mContext.report(
                        ICON_EXPECTED_SIZE,
                        node,
                        mContext.getCallLocation(node, true, false),
                        "Ensure that icons set programmatically follow the density-specific "
                                + "size conventions for launcher icons.");
            }
            return super.visitCallExpression(node);
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if (name != null
                    && (name.startsWith("ic_launcher") || name.startsWith("icon_"))) {
                mContext.report(
                        ICON_EXPECTED_SIZE,
                        node,
                        mContext.getLocation(node),
                        "Ensure icon `" + name + "` follows the density-specific "
                                + "size conventions: "
                                + MDPI_SIZE + "x" + MDPI_SIZE + " (mdpi), "
                                + HDPI_SIZE + "x" + HDPI_SIZE + " (hdpi), "
                                + XHDPI_SIZE + "x" + XHDPI_SIZE + " (xhdpi), "
                                + XXHDPI_SIZE + "x" + XXHDPI_SIZE + " (xxhdpi), "
                                + XXXHDPI_SIZE + "x" + XXXHDPI_SIZE + " (xxxhdpi).");
            }
            return super.visitSimpleNameReferenceExpression(node);
        }
    }
}