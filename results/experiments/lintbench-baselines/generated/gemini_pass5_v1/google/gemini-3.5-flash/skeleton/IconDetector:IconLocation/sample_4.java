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
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in `drawable-ldpi`, "
                            + "`drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density "
                            + "independent (for example a solid color) you can place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        for (File resFolder : context.getProject().getResourceFolders()) {
            File[] subFolders = resFolder.listFiles();
            if (subFolders != null) {
                for (File subFolder : subFolders) {
                    if (subFolder.isDirectory()) {
                        String name = subFolder.getName();
                        if (name.startsWith("drawable") && (name.equals("drawable") || name.startsWith("drawable-"))) {
                            if (!isDensityFolder(name)) {
                                File[] files = subFolder.listFiles();
                                if (files != null) {
                                    for (File file : files) {
                                        if (isBitmapFile(file)) {
                                            Incident incident = new Incident(ISSUE);
                                            incident.setFile(file);
                                            incident.setLocation(Location.create(file));
                                            incident.setMessage("Bitmap file should be in a density-specific folder (e.g. `drawable-mdpi`) instead of `" + name + "`");
                                            context.report(incident);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isDensityFolder(String name) {
        if (name.contains("-ldpi") ||
            name.contains("-mdpi") ||
            name.contains("-hdpi") ||
            name.contains("-xhdpi") ||
            name.contains("-xxhdpi") ||
            name.contains("-xxxhdpi") ||
            name.contains("-tvdpi") ||
            name.contains("-nodpi") ||
            name.contains("-anydpi")) {
            return true;
        }
        return name.matches(".*-\\d+dpi.*");
    }

    private boolean isBitmapFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") ||
               name.endsWith(".jpg") ||
               name.endsWith(".jpeg") ||
               name.endsWith(".gif") ||
               name.endsWith(".webp");
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
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
        return Collections.emptyList();
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