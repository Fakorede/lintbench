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
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
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
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The `res/drawable` folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Bitmap file extensions that should not be placed in density-independent drawable folder */
    private static final String[] BITMAP_EXTENSIONS = {
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp"
    };

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to initialize before checking the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to finalize after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Allow all incidents through
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We want to check bitmap elements in XML drawable files
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this bitmap element is in the plain drawable folder (density-independent)
        File file = context.file;
        File folder = file.getParentFile();
        if (folder != null) {
            String folderName = folder.getName();
            // Only flag if in the plain "drawable" folder (not drawable-mdpi, drawable-hdpi, etc.)
            if (folderName.equals("drawable")) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Bitmaps should not be defined in the `drawable` folder; move it to "
                                + "`drawable-mdpi` and provide versions in `drawable-ldpi`, "
                                + "`drawable-hdpi`, and `drawable-xhdpi` as well");
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this detector
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for this detector
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this detector
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for this detector
            }
        };
    }

    /**
     * Checks whether the given file name has a bitmap extension.
     *
     * @param fileName the file name to check
     * @return true if the file name ends with a bitmap extension
     */
    private static boolean isBitmapFile(@NonNull String fileName) {
        String lowerName = fileName.toLowerCase();
        for (String ext : BITMAP_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given folder name is the plain density-independent drawable folder.
     *
     * @param folderName the folder name to check
     * @return true if the folder is the plain "drawable" folder
     */
    private static boolean isPlainDrawableFolder(@NonNull String folderName) {
        return folderName.equals("drawable");
    }
}