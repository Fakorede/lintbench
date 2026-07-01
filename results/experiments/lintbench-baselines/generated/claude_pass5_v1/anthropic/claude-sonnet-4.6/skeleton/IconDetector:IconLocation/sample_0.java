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
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
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
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                    IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/guide/practices/screens_support.html");

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        // We want to be notified for any element in a drawable XML file,
        // but we'll check at the file level. Return null to not filter by element name,
        // or return a specific list. We'll use a different approach and check in
        // beforeCheckFile instead.
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used since getApplicableElements returns empty list
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (isInPlainDrawableFolder(file) && isBitmapFile(file)) {
            String message =
                    String.format(
                            "The `%1$s` file is in the `drawable` folder which is not density-specific. "
                                    + "Consider moving it to `drawable-mdpi` and providing higher and lower "
                                    + "resolution versions in `drawable-ldpi`, `drawable-hdpi` and "
                                    + "`drawable-xhdpi`. If the icon is density independent, place it in "
                                    + "`drawable-nodpi`.",
                            file.getName());
            context.report(ISSUE, context.getLocation(file), message);
        }
    }

    /**
     * Returns true if the given file is in the plain "drawable" folder (not a density-qualified
     * variant like drawable-mdpi, drawable-hdpi, drawable-nodpi, etc.)
     */
    private static boolean isInPlainDrawableFolder(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String parentName = parent.getName();
        // The plain drawable folder is exactly "drawable" with no qualifiers
        return parentName.equals("drawable");
    }

    /**
     * Returns true if the given file is a bitmap image file (png, jpg, jpeg, gif, webp, bmp).
     */
    private static boolean isBitmapFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Nothing to do here
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // No filtering needed; always report the incident
        return true;
    }
}