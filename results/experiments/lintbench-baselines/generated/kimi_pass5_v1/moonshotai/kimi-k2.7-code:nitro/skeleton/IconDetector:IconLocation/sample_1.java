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
            new Implementation(
                    IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The `res/drawable` folder is intended for density-independent graphics such as shapes defined in XML. "
                            + "For bitmaps, move the file to `drawable-mdpi` and consider providing higher and lower "
                            + "resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "really is density independent (for example a solid color) you can place it in "
                            + "`drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {}

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        File resourceDir = context.getProject().getResourceDir();
        if (resourceDir == null) {
            return;
        }

        File drawable = new File(resourceDir, "drawable");
        if (!drawable.isDirectory()) {
            return;
        }

        File[] files = drawable.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && isBitmapDrawable(file)) {
                String message =
                        "Bitmap drawable `"
                                + file.getName()
                                + "` should not be placed in the density-independent `res/drawable` folder.";
                Incident incident = new Incident(ISSUE, context.getLocation(file), message);
                context.report(incident);
            }
        }
    }

    private static boolean isBitmapDrawable(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
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
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {}

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {}

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {}

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {}
        };
    }
}