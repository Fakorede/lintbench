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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
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
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                            + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                            + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                            + "a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File res : resourceFolders) {
            java.io.File[] subfolders = res.listFiles();
            if (subfolders != null) {
                for (java.io.File subfolder : subfolders) {
                    if (subfolder.isDirectory() && isDensityIndependentDrawableFolder(subfolder.getName())) {
                        java.io.File[] files = subfolder.listFiles();
                        if (files != null) {
                            for (java.io.File file : files) {
                                if (file.isFile() && isBitmap(file.getName())) {
                                    Location location = Location.create(file);
                                    Incident incident = new Incident(
                                            ISSUE,
                                            "The `res/drawable` folder is intended for density-independent graphics such as "
                                                    + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                                                    + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                                                    + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                                                    + "a solid color) you can place it in `drawable-nodpi`.",
                                            location
                                    );
                                    incident.file(file);
                                    context.report(incident);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isDensityIndependentDrawableFolder(String folderName) {
        if (!folderName.startsWith("drawable")) {
            return false;
        }
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi")
                    || segment.equals("mdpi")
                    || segment.equals("hdpi")
                    || segment.equals("xhdpi")
                    || segment.equals("xxhdpi")
                    || segment.equals("xxxhdpi")
                    || segment.equals("tvdpi")
                    || segment.equals("anydpi")
                    || segment.equals("nodpi")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBitmap(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
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
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
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
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            }
        };
    }
}