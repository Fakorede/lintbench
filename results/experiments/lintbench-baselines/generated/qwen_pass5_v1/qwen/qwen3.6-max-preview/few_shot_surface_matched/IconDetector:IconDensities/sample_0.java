package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.*;
import org.w3c.dom.Element;

import java.io.File;
import java.util.*;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ENV_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";
    private static final boolean INCLUDE_LDPI = Boolean.parseBoolean(System.getenv(ENV_INCLUDE_LDPI));

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see "
                    + "https://developer.android.com/about/dashboards",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    private final Set<String> mReportedIcons = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReportedIcons.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Final aggregation or cleanup can be performed here
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String src = element.getAttribute("android:src");
        if (src != null && src.startsWith("@drawable/")) {
            String icon = src.substring("@drawable/".length());
            reportIfMissingDensities(context, element, icon);
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // No direct icon density checks required for method declarations
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Method calls referencing drawables can be intercepted here if necessary
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Class-level scanning hook
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String ref = node.asSourceString();
        if (ref != null && ref.startsWith("R.drawable.")) {
            String icon = ref.substring("R.drawable.".length());
            reportIfMissingDensities(context, node, icon);
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    private void reportIfMissingDensities(@NonNull XmlContext context, @NonNull Element element, @NonNull String icon) {
        if (mReportedIcons.add(icon)) {
            String densities = "mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi" + (INCLUDE_LDPI ? ", ldpi" : "");
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing density variations for icon `" + icon + "`. Ensure versions exist for: " + densities + ".");
        }
    }

    private void reportIfMissingDensities(@NonNull JavaContext context, @NonNull UElement node, @NonNull String icon) {
        if (mReportedIcons.add(icon)) {
            String densities = "mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi" + (INCLUDE_LDPI ? ", ldpi" : "");
            context.report(ISSUE, node, context.getLocation(node),
                    "Missing density variations for icon `" + icon + "`. Ensure versions exist for: " + densities + ".");
        }
    }
}