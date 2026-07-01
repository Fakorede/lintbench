package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.SourceCodeScanner;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine-patch file will both map to the same "
                            + "drawable resource, `@drawable/file`, which is probably not what was "
                            + "intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do here; per-project analysis happens in afterCheckEachProject.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> files = project.getResourceFiles();
        if (files == null || files.isEmpty()) {
            return;
        }

        Map<String, File> seen = new HashMap<>();
        Set<String> reported = new HashSet<>();

        for (File file : files) {
            File parent = file.getParentFile();
            if (parent == null) {
                continue;
            }

            ResourceFolderType folderType = ResourceFolderType.getFolderType(parent.getName());
            if (folderType != ResourceFolderType.DRAWABLE) {
                continue;
            }

            String fileName = file.getName();
            if (fileName.endsWith(".9.png")) {
                String base = fileName.substring(0, fileName.length() - ".9.png".length());
                checkClash(context, file, base, seen, reported, true);
            } else if (fileName.endsWith(".png")) {
                String base = fileName.substring(0, fileName.length() - ".png".length());
                checkClash(context, file, base, seen, reported, false);
            }
        }
    }

    private void checkClash(
            @NonNull Context context,
            @NonNull File file,
            @NonNull String base,
            @NonNull Map<String, File> seen,
            @NonNull Set<String> reported,
            boolean isNinePatch) {
        File other = seen.get(base);
        if (other == null) {
            seen.put(base, file);
            return;
        }

        boolean otherIsNinePatch = other.getName().endsWith(".9.png");
        if (otherIsNinePatch != isNinePatch && !reported.contains(base)) {
            reported.add(base);
            String message =
                    String.format(
                            "The drawable resource `%1$s` is defined by both `%2$s` and `%3$s`; "
                                    + "both map to `@drawable/%1$s`.",
                            base, other.getName(), file.getName());
            context.report(new Incident(ISSUE, Location.create(file), message));
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
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used for this check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for this check.
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
                // Not used for this check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for this check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // Not used for this check.
            }
        };
    }
}