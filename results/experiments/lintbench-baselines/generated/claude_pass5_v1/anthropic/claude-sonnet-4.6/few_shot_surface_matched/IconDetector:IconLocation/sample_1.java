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
                                    + "resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                                    + "and `drawable-xhdpi`. If the icon **really** is density "
                                    + "independent (for example a solid color) you can place it "
                                    + "in `drawable-nodpi`.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/practices/screens_support.html");

    private static final List<String> BITMAP_EXTENSIONS =
            Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");

    public IconDetector() {}

    // -------------------------------------------------------------------------
    // Detector lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize for this check.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to finalize for this check.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // Allow all incidents through.
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE;
    }

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        // We want to visit the root element of every drawable XML file
        // to detect bitmaps placed in the plain drawable folder.
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        File file = context.file;
        if (!isInPlainDrawableFolder(file)) {
            return;
        }

        String tagName = element.getTagName();
        if ("bitmap".equals(tagName)) {
            Location location = context.getLocation(element);
            context.report(
                    ICON_LOCATION,
                    element,
                    location,
                    "Bitmap `"
                            + file.getName()
                            + "` is located in a density-independent drawable folder; "
                            + "consider moving it to `drawable-mdpi` or using `drawable-nodpi`");
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    @Override
    @Nullable
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    @Nullable
    public com.android.tools.lint.detector.api.UastCallVisitor createUastHandler(
            @NonNull JavaContext context) {
        return null;
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Not used in this detector.
    }

    @Override
    public void visitCallExpression(
            @NonNull JavaContext context, @NonNull UCallExpression call) {
        // Not used in this detector.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used in this detector.
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression node) {
        // Not used in this detector.
    }

    // -------------------------------------------------------------------------
    // Resource file check (non-XML bitmaps)
    // -------------------------------------------------------------------------

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (!isInPlainDrawableFolder(file)) {
            return;
        }

        String name = file.getName().toLowerCase(java.util.Locale.US);
        for (String ext : BITMAP_EXTENSIONS) {
            if (name.endsWith(ext)) {
                Location location = Location.create(file);
                context.report(
                        ICON_LOCATION,
                        location,
                        "The file `"
                                + file.getName()
                                + "` is a bitmap placed in the density-independent `drawable` "
                                + "folder; consider moving it to `drawable-mdpi` and providing "
                                + "versions in other density buckets, or use `drawable-nodpi` "
                                + "if the image is truly density-independent");
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isInPlainDrawableFolder(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        // The folder name must be exactly "drawable" (no qualifier suffix).
        String folderName = parent.getName();
        return "drawable".equals(folderName);
    }
}