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
import com.android.tools.lint.detector.api.XmlContext;
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

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FILE));

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

    /** Bitmap file extensions */
    private static final String[] BITMAP_EXTENSIONS = {
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"
    };

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We handle this at the file level, not element level
        return Collections.emptyList();
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do before checking root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to do after checking each project
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used since getApplicableElements returns empty list
    }

    /**
     * Check resource files directly. We override beforeCheckFile to inspect
     * whether the file is a bitmap in the plain drawable folder.
     */
    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (isBitmapFile(file)) {
            // Check if the parent folder is the plain "drawable" folder (no qualifier)
            File parentFolder = file.getParentFile();
            if (parentFolder != null) {
                String parentName = parentFolder.getName();
                if (parentName.equals("drawable")) {
                    // This is a bitmap in the density-independent drawable folder
                    String message =
                            String.format(
                                    "The `%1$s` folder is intended for density-independent graphics "
                                            + "such as shapes defined in XML. For bitmaps, move it to "
                                            + "`drawable-mdpi` and consider providing higher and lower "
                                            + "resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                                            + "and `drawable-xhdpi`. If the icon **really** is density "
                                            + "independent (for example a solid color) you can place it "
                                            + "in `drawable-nodpi`.",
                                    parentName);
                    context.report(
                            new Incident(
                                    ISSUE,
                                    com.android.tools.lint.detector.api.Location.create(file),
                                    message));
                }
            }
        }
    }

    /**
     * Returns true if the given file is a bitmap file based on its extension.
     */
    private static boolean isBitmapFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        for (String extension : BITMAP_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    // The following methods are stubs required by the skeleton but not needed for this detector

    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }

    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used
            }
        };
    }
}