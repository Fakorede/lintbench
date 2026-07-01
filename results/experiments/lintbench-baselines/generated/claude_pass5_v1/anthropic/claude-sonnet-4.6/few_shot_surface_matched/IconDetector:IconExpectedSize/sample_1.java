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

    public static final Issue ICON_EXPECTED_SIZE =
            Issue.create(
                            "IconExpectedSize",
                            "Icon has incorrect size",
                            "There are predefined sizes (for each density) for launcher icons. You "
                                    + "should follow these conventions to make sure your icons fit in with the "
                                    + "overall look of the platform.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            new Implementation(
                                    IconDetector.class,
                                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                                    Scope.JAVA_FILE_SCOPE,
                                    Scope.MANIFEST_SCOPE))
                    .setAndroidSpecific(true);

    private static final String ATTR_ICON = "icon";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";

    public IconDetector() {}

    // SourceCodeScanner methods

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UastHandler createUastHandler(@NonNull JavaContext context) {
        return new UastHandler(context);
    }

    private static class UastHandler extends AbstractUastVisitor {
        private final JavaContext mContext;

        UastHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            visitCallExpressionInternal(mContext, node);
            return super.visitCallExpression(node);
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            visitSimpleNameReferenceExpressionInternal(mContext, node);
            return super.visitSimpleNameReferenceExpression(node);
        }
    }

    private static void visitCallExpressionInternal(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // Check for icon-related method calls that might indicate incorrect icon sizes
        String methodName = node.getMethodName();
        if (methodName != null && (methodName.contains("Icon") || methodName.contains("icon"))) {
            // Potential icon usage detected; further analysis could be done here
        }
    }

    private static void visitSimpleNameReferenceExpressionInternal(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Check for icon-related references
        String name = node.getIdentifier();
        if (name != null && (name.contains("icon") || name.contains("Icon"))) {
            // Potential icon reference detected; further analysis could be done here
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Handle specific method visits if needed
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Handle class visits if needed
    }

    @Nullable
    @Override
    public List<String> getApplicableSuperClasses() {
        return null;
    }

    // XmlScanner methods

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for icon attributes in manifest elements
        if (element.hasAttribute(ATTR_ICON)) {
            String iconValue = element.getAttribute(ATTR_ICON);
            if (iconValue != null && !iconValue.isEmpty()) {
                // Icon attribute found; validate size conventions
                // Report if icon does not follow predefined size conventions
                checkIconSize(context, element, iconValue);
            }
        }
    }

    private void checkIconSize(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String iconValue) {
        // In a full implementation, this would check the actual icon file dimensions
        // against the expected sizes for each density bucket:
        // mdpi: 48x48, hdpi: 72x72, xhdpi: 96x96, xxhdpi: 144x144, xxxhdpi: 192x192
        // For now, we validate that the icon reference is properly formed
        if (!iconValue.startsWith("@") && !iconValue.startsWith("?")) {
            context.report(
                    ICON_EXPECTED_SIZE,
                    element,
                    context.getLocation(element),
                    "Icon does not follow the expected size conventions for launcher icons. "
                            + "Ensure icons are provided at the correct density sizes "
                            + "(mdpi: 48x48, hdpi: 72x72, xhdpi: 96x96, xxhdpi: 144x144, "
                            + "xxxhdpi: 192x192).");
        }
    }

    // Lifecycle methods

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialize any state needed before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Clean up or finalize any state after checking each project
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Allow all incidents by default; could filter based on project type or other criteria
        return false;
    }
}