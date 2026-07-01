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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.ResourceContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner, SourceCodeScanner, XmlScanner {

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

    private final java.util.Set<java.io.File> reportedFiles =
            java.util.Collections.synchronizedSet(new java.util.HashSet<java.io.File>());

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        reportedFiles.clear();
        checkProject(context);
    }

    private void checkProject(@NonNull Context context) {
        for (java.io.File resDir : context.getProject().getResourceFolders()) {
            java.io.File drawableDir = new java.io.File(resDir, "drawable");
            if (drawableDir.isDirectory()) {
                java.io.File[] files = drawableDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        String name = file.getName();
                        if (isBitmapFile(name)) {
                            reportFile(context, file);
                        }
                    }
                }
            }
        }
    }

    private void reportFile(@NonNull Context context, @NonNull java.io.File file) {
        if (reportedFiles.add(file)) {
            Incident incident = new Incident();
            incident.setIssue(ISSUE);
            incident.setMessage("Keep bitmap files in density-specific folders");
            incident.setLocation(context.getLocation(file));
            context.report(incident);
        }
    }

    private static boolean isBitmapFile(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        java.io.File file = context.file;
        java.io.File parent = file.getParentFile();
        if (parent != null && "drawable".equals(parent.getName())) {
            if (isBitmapFile(file.getName())) {
                reportFile(context, file);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
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
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
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
                // No-op
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // No-op
            }
        };
    }
}