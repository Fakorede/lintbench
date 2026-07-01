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
import com.android.tools.lint.detector.api.Location;
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
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ICON_LOCATION =
            Issue.create(
                            "IconLocation",
                            "Image defined in density-independent drawable folder",
                            "The `res/drawable` folder is intended for density-independent graphics "
                                    + "such as shapes defined in XML. For bitmaps, move it to "
                                    + "`drawable-mdpi` and consider providing higher and lower "
                                    + "resolution versions in `drawable-ldpi`, `drawable-hdpi` and "
                                    + "`drawable-xhdpi`. If the icon **really** is density "
                                    + "independent (for example a solid color) you can place it in "
                                    + "`drawable-nodpi`.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/practices/screens_support.html");

    private static final List<String> BITMAP_EXTENSIONS =
            Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization before checking root project if needed
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        if (!project.isAndroidProject()) {
            return;
        }

        File resourceDir = null;
        List<File> resDirs = project.getResourceFolders();
        if (resDirs == null || resDirs.isEmpty()) {
            return;
        }

        for (File resDir : resDirs) {
            File drawableDir = new File(resDir, "drawable");
            if (drawableDir.exists() && drawableDir.isDirectory()) {
                File[] files = drawableDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (isBitmapFile(file)) {
                            String message =
                                    "Found bitmap drawable `res/drawable/"
                                            + file.getName()
                                            + "` in densityless folder; Should be placed in a "
                                            + "density-specific folder (`drawable-mdpi`, etc.)";
                            Location location = Location.create(file);
                            context.report(
                                    new Incident(
                                            ICON_LOCATION,
                                            location,
                                            message));
                        }
                    }
                }
            }
        }
    }

    private static boolean isBitmapFile(@NonNull File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        for (String ext : BITMAP_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull Object cookie) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element visiting needed for this check
    }

    @Nullable
    @Override
    public com.android.tools.lint.client.api.UElementHandler createUastHandler(
            @NonNull JavaContext context) {
        return new com.android.tools.lint.client.api.UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No specific handling needed
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No specific handling needed
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                // No specific handling needed
            }
        };
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UClass.class);
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No specific method call handling needed
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No specific class visiting needed
    }

    /**
     * Called when visiting a method reference in UAST (not a method call). Satisfies
     * SourceCodeScanner#visitMethod if the interface requires it.
     */
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No specific handling needed
    }

    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // No specific handling needed
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No specific handling needed
    }
}