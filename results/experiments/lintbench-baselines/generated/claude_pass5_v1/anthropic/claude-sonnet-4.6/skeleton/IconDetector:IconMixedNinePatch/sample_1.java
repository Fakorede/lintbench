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
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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

public class IconDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, Scope.ALL_RESOURCES_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same drawable "
                            + "resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Map from resource name (without extension) to list of files that map to it
    private Map<String, List<File>> mFileMap;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileMap = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mFileMap == null) {
            return;
        }

        for (Map.Entry<String, List<File>> entry : mFileMap.entrySet()) {
            List<File> files = entry.getValue();
            if (files.size() >= 2) {
                boolean hasPng = false;
                boolean hasNinePatch = false;
                File pngFile = null;
                File ninePatchFile = null;

                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".9.png")) {
                        hasNinePatch = true;
                        ninePatchFile = file;
                    } else if (name.endsWith(".png")) {
                        hasPng = true;
                        pngFile = file;
                    }
                }

                if (hasPng && hasNinePatch && pngFile != null && ninePatchFile != null) {
                    String resourceName = entry.getKey();
                    Location location = Location.create(ninePatchFile);
                    Location secondary = Location.create(pngFile);
                    secondary.setMessage("Also maps to @drawable/" + resourceName);
                    location.setSecondary(secondary);

                    String message =
                            String.format(
                                    "The files `%1$s` and `%2$s` both map to the same drawable "
                                            + "resource id `@drawable/%3$s`",
                                    ninePatchFile.getName(),
                                    pngFile.getName(),
                                    resourceName);

                    context.report(
                            new Incident(ISSUE, location, message));
                }
            }
        }

        mFileMap = null;
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }

    /**
     * Called for each resource file. We use this to track PNG and 9-PNG files.
     */
    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mFileMap == null) {
            return;
        }

        File file = context.file;
        String name = file.getName();

        String resourceName = null;
        if (name.endsWith(".9.png")) {
            // Nine-patch file: strip .9.png
            resourceName = name.substring(0, name.length() - ".9.png".length());
        } else if (name.endsWith(".png")) {
            // Regular PNG: strip .png
            resourceName = name.substring(0, name.length() - ".png".length());
        }

        if (resourceName != null && !resourceName.isEmpty()) {
            List<File> files = mFileMap.get(resourceName);
            if (files == null) {
                files = new ArrayList<>();
                mFileMap.put(resourceName, files);
            }
            // Only add if not already present
            if (!files.contains(file)) {
                files.add(file);
            }
        }
    }

    // The following methods are stubs required by the skeleton but not used in this detector

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