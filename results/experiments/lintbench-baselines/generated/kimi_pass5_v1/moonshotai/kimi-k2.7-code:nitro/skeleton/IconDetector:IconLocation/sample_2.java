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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String BITMAP = "bitmap";
    private static final String NINE_PATCH = "nine-patch";
    private static final String FOLDER_DRAWABLE = "drawable";
    private static final String FOLDER_NO_DPI = "drawable-nodpi";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The `res/drawable` folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move them to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in "
                            + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "really is density independent (for example a solid color) you can "
                            + "place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Set<String> mNoDpiIcons;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNoDpiIcons = new HashSet<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mNoDpiIcons.clear();

        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();

        for (File res : resourceFolders) {
            File noDpi = new File(res, FOLDER_NO_DPI);
            if (noDpi.isDirectory()) {
                File[] files = noDpi.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isImageFile(file)) {
                            mNoDpiIcons.add(getBaseName(file));
                        }
                    }
                }
            }
        }

        for (File res : resourceFolders) {
            File drawable = new File(res, FOLDER_DRAWABLE);
            if (drawable.isDirectory()) {
                File[] files = drawable.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isImageFile(file)) {
                            String baseName = getBaseName(file);
                            if (!mNoDpiIcons.contains(baseName)) {
                                reportIconLocation(context, file);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(BITMAP, NINE_PATCH);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        File parent = context.file.getParentFile();
        if (parent == null) {
            return;
        }

        String folder = parent.getName();
        if (!FOLDER_DRAWABLE.equals(folder)) {
            return;
        }

        String tag = element.getTagName();
        if (!BITMAP.equals(tag) && !NINE_PATCH.equals(tag)) {
            return;
        }

        String message =
                String.format(
                        Locale.US,
                        "The `%1$s` drawable `%2$s` is defined in the density-independent "
                                + "`drawable` folder. For bitmaps, move it to `drawable-mdpi` and "
                                + "provide other densities, or use `drawable-nodpi` if it is truly "
                                + "density independent.",
                        tag,
                        context.file.getName());

        context.report(new Incident(ISSUE, context.getLocation(element), message));
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this issue.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for this issue.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this issue.
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not used for this issue.
            }
        };
    }

    private static void reportIconLocation(@NonNull Context context, @NonNull File file) {
        String message =
                String.format(
                        Locale.US,
                        "The image `res/drawable/%1$s` is defined in the density-independent "
                                + "`drawable` folder. For bitmaps, move it to `drawable-mdpi` and "
                                + "provide other densities, or use `drawable-nodpi` if it is truly "
                                + "density independent.",
                        file.getName());

        context.report(new Incident(ISSUE, Location.create(file), message));
    }

    private static boolean isImageFile(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    private static String getBaseName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".9.png")) {
            return name.substring(0, name.length() - ".9.png".length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}