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
import com.android.tools.lint.detector.api.LintMap;
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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

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

    private static final String DRAWABLE_FOLDER = "drawable";

    public IconDetector() {}

    // ---- Implements Detector ----

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Hook for any initialization before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Hook for any cleanup or reporting after each project is checked
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents through by default
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        // Apply to all files in drawable folders
        String parentName = file.getParentFile() != null ? file.getParentFile().getName() : "";
        return parentName.equals(DRAWABLE_FOLDER) || parentName.startsWith(DRAWABLE_FOLDER + "-");
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        // We want to visit bitmap elements in XML drawable files
        return Arrays.asList("bitmap", "nine-patch");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this bitmap element is in the plain drawable folder
        File file = context.file;
        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            return;
        }

        String folderName = parentFolder.getName();
        if (folderName.equals(DRAWABLE_FOLDER)) {
            Location location = context.getLocation(element);
            String message =
                    "Bitmaps should not be defined in the `drawable` folder; move it to "
                            + "`drawable-mdpi` and consider providing versions for other densities "
                            + "in `drawable-ldpi`, `drawable-hdpi`, and `drawable-xhdpi`";
            Incident incident = new Incident(ICON_LOCATION, element, location, message);
            context.report(incident);
        }
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression node) {
        // No-op: placeholder for handling call expressions if needed
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // No-op: placeholder for handling simple name references if needed
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // No-op: placeholder for method visit
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op: placeholder for class visit
    }

    // ---- Helper ----

    /**
     * Checks whether the given file resides in the plain, density-unqualified
     * {@code drawable} folder (i.e. not {@code drawable-mdpi}, {@code drawable-nodpi}, etc.).
     */
    private static boolean isInPlainDrawableFolder(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        return parent.getName().equals(DRAWABLE_FOLDER);
    }

    /**
     * Returns {@code true} if the filename has a bitmap extension.
     */
    private static boolean isBitmapFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
    }
}