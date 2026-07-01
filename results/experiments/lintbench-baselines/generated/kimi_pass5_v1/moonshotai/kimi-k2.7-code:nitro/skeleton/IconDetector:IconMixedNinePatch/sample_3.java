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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                    "If a drawable folder contains both `file.png` and `file.9.png`, both map to the same `@drawable/file` resource, which is usually not intended. Use distinct base names for PNG and nine-patch assets.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No shared state is needed for this check.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File resourceFolder : resourceFolders) {
            File[] drawableFolders = resourceFolder.listFiles();
            if (drawableFolders == null) {
                continue;
            }

            for (File folder : drawableFolders) {
                if (!folder.isDirectory()) {
                    continue;
                }
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
                if (folderType != ResourceFolderType.DRAWABLE) {
                    continue;
                }

                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }

                    String name = file.getName();
                    String baseName;
                    boolean isNinePatch;
                    if (name.endsWith(".9.png")) {
                        baseName = name.substring(0, name.length() - ".9.png".length());
                        isNinePatch = true;
                    } else if (name.endsWith(".png")) {
                        baseName = name.substring(0, name.length() - ".png".length());
                        isNinePatch = false;
                    } else {
                        continue;
                    }

                    if (baseName.isEmpty()) {
                        continue;
                    }

                    if (isNinePatch) {
                        File png = pngFiles.get(baseName);
                        if (png != null) {
                            reportClash(context, file, png, baseName);
                        }
                        ninePatchFiles.put(baseName, file);
                    } else {
                        File ninePatch = ninePatchFiles.get(baseName);
                        if (ninePatch != null) {
                            reportClash(context, ninePatch, file, baseName);
                        }
                        pngFiles.put(baseName, file);
                    }
                }
            }
        }
    }

    private void reportClash(
            @NonNull Context context,
            @NonNull File ninePatchFile,
            @NonNull File pngFile,
            @NonNull String baseName) {
        String message =
                "The drawable resource `@drawable/"
                        + baseName
                        + "` is defined by both `"
                        + ninePatchFile.getName()
                        + "` and `"
                        + pngFile.getName()
                        + "`; a PNG and a nine-patch should not share the same base name.";

        Location location = Location.create(ninePatchFile);
        location.setSecondary(Location.create(pngFile));

        context.report(new Incident(ISSUE, location, message));
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
        // Not needed for this issue.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not needed for this issue.
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
                // Not needed for this issue.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not needed for this issue.
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not needed for this issue.
            }
        };
    }
}