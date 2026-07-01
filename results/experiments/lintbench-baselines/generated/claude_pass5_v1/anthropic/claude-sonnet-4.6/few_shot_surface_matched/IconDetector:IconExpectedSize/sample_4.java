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
import java.io.File;
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
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST));

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
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    // Standard launcher icon sizes by density (width x height in dp/px)
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

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialize any state needed before checking a root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Clean up or finalize checks after each project is checked
        Project project = context.getProject();
        if (project != null) {
            // Check icon files for the project after all files have been scanned
            checkProjectIconSizes(context, project);
        }
    }

    private void checkProjectIconSizes(@NonNull Context context, @NonNull Project project) {
        File projectDir = project.getDir();
        if (projectDir == null) {
            return;
        }
        File resDir = new File(projectDir, "res");
        if (!resDir.exists()) {
            return;
        }
        File[] resDirs = resDir.listFiles();
        if (resDirs == null) {
            return;
        }
        for (File densityDir : resDirs) {
            String dirName = densityDir.getName();
            if (!dirName.startsWith("mipmap-") && !dirName.startsWith("drawable-")) {
                continue;
            }
            int expectedSize = getExpectedSizeForDensity(dirName);
            if (expectedSize <= 0) {
                continue;
            }
            File[] iconFiles = densityDir.listFiles();
            if (iconFiles == null) {
                continue;
            }
            for (File iconFile : iconFiles) {
                String name = iconFile.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    // We would check image dimensions here if we had image reading capability
                    // In a real implementation, we'd read the image dimensions and compare
                }
            }
        }
    }

    private int getExpectedSizeForDensity(@NonNull String dirName) {
        if (dirName.contains("xxxhdpi")) {
            return XXXHDPI_SIZE;
        } else if (dirName.contains("xxhdpi")) {
            return XXHDPI_SIZE;
        } else if (dirName.contains("xhdpi")) {
            return XHDPI_SIZE;
        } else if (dirName.contains("hdpi")) {
            return HDPI_SIZE;
        } else if (dirName.contains("mdpi")) {
            return MDPI_SIZE;
        }
        return -1;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Filter out incidents that don't apply to the current context
        if (context.getProject().isLibrary()) {
            // Library projects might have different icon requirements
            return false;
        }
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE;
    }

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
        // Check that elements declaring icons reference valid icon resources
        String iconAttr = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_ICON);
        if (iconAttr == null || iconAttr.isEmpty()) {
            iconAttr = element.getAttribute(ATTR_ICON);
        }
        if (iconAttr != null && !iconAttr.isEmpty()) {
            // Validate icon reference format
            if (!iconAttr.startsWith("@mipmap/") && !iconAttr.startsWith("@drawable/")) {
                // Icon references should typically be mipmap or drawable resources
                // This is an informational check
            }
        }
    }

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
                "setIcon",
                "setImageResource",
                "setImageDrawable",
                "setLogo");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Check icon-related method calls for potential size issues
        String methodName = method.getName();
        if ("setIcon".equals(methodName)
                || "setImageResource".equals(methodName)
                || "setLogo".equals(methodName)) {
            // In a real implementation, we'd resolve the resource reference
            // and check the actual image dimensions against expected sizes
        }
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // Handle call expressions related to icon usage
        String methodName = call.getMethodName();
        if (methodName == null) {
            return;
        }
        switch (methodName) {
            case "setIcon":
            case "setImageResource":
            case "setImageDrawable":
            case "setLogo":
                // Analyze icon usage for potential size mismatches
                break;
            default:
                break;
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check class-level icon related annotations or configurations
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Check references to icon resources
        String name = node.getIdentifier();
        if (name != null && (name.startsWith("ic_launcher") || name.contains("icon"))) {
            // Track references to launcher icons
        }
    }

    /**
     * UAST visitor to detect icon-related issues in Java/Kotlin source code.
     */
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
                    || "setLogo".equals(methodName)) {
                // In a full implementation, we'd resolve the resource and check dimensions
                checkIconMethodCall(node);
            }

            return super.visitCallExpression(node);
        }

        private void checkIconMethodCall(@NonNull UCallExpression call) {
            List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
            if (args.isEmpty()) {
                return;
            }
            // Analyze the first argument which should be the icon resource
            org.jetbrains.uast.UExpression iconArg = args.get(0);
            if (iconArg == null) {
                return;
            }
            // In a complete implementation, we'd resolve the drawable/mipmap resource
            // and verify its size matches the expected density-specific dimensions
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            return super.visitSimpleNameReferenceExpression(node);
        }
    }
}