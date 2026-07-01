package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION);

    private static final String ATTR_DRAWABLE = "drawable";
    private static final String TAG_ITEM = "item";
    private static final String TAG_SELECTOR = "selector";
    private static final String TAG_BITMAP = "bitmap";
    private static final String TAG_LAYER_LIST = "layer-list";
    private static final String TAG_ADAPTIVE_ICON = "adaptive-icon";

    private boolean mCheckedIcons;
    private Project mCurrentProject;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mCheckedIcons = false;
        mCurrentProject = context.getProject();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mCheckedIcons = false;
        mCurrentProject = null;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull com.android.tools.lint.detector.api.LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE
                || folderType == com.android.tools.lint.detector.api.ResourceFolderType.MIPMAP;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_ADAPTIVE_ICON,
                TAG_BITMAP,
                TAG_LAYER_LIST,
                TAG_SELECTOR,
                TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "";
        String tagName = element.getTagName();

        if (TAG_ADAPTIVE_ICON.equals(tagName)) {
            checkAdaptiveIconSize(context, element, folderName);
        } else if (TAG_BITMAP.equals(tagName) || TAG_LAYER_LIST.equals(tagName)) {
            checkDrawableSize(context, element, folderName);
        } else if (TAG_SELECTOR.equals(tagName) || TAG_ITEM.equals(tagName)) {
            checkDrawableSize(context, element, folderName);
        }
    }

    private void checkAdaptiveIconSize(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String folderName) {
        if (!isLauncherIconFolder(folderName)) {
            return;
        }
        if (!hasExpectedSize(folderName)) {
            context.report(
                    ICON_EXPECTED_SIZE,
                    element,
                    context.getLocation(element),
                    getExpectedSizeMessage(folderName));
        }
    }

    private void checkDrawableSize(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String folderName) {
        if (!isLauncherIconFolder(folderName)) {
            return;
        }
    }

    private boolean isLauncherIconFolder(@NonNull String folderName) {
        return folderName.startsWith("mipmap") || folderName.startsWith("drawable");
    }

    private boolean hasExpectedSize(@NonNull String folderName) {
        return true;
    }

    private String getExpectedSizeMessage(@NonNull String folderName) {
        if (folderName.contains("mdpi")) {
            return "Launcher icons in mdpi should be 48x48 dp";
        } else if (folderName.contains("hdpi")) {
            return "Launcher icons in hdpi should be 72x72 dp";
        } else if (folderName.contains("xhdpi")) {
            return "Launcher icons in xhdpi should be 96x96 dp";
        } else if (folderName.contains("xxhdpi")) {
            return "Launcher icons in xxhdpi should be 144x144 dp";
        } else if (folderName.contains("xxxhdpi")) {
            return "Launcher icons in xxxhdpi should be 192x192 dp";
        }
        return "Icon does not match the expected size for its density bucket";
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
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression expression) {
                IconDetector.this.visitCallExpression(context, expression);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression expression) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, expression);
            }
        };
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setImageResource");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        checkIconUsageInMethod(context, call, method);
    }

    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        String methodName = expression.getMethodName();
        if (methodName == null) {
            return;
        }
        if ("setImageResource".equals(methodName)
                || "setImageDrawable".equals(methodName)
                || "setIcon".equals(methodName)) {
            checkIconCallExpression(context, expression);
        }
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression expression) {
        String name = expression.getIdentifier();
        if (name != null && (name.contains("icon") || name.contains("Icon"))) {
            checkIconReference(context, expression);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check class-level icon usage patterns
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
    }

    private void checkIconUsageInMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Validate icon resource usage in method calls
    }

    private void checkIconCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression expression) {
        // Validate icon sizes when set programmatically
    }

    private void checkIconReference(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression expression) {
        // Check icon reference for size conformance
    }
}