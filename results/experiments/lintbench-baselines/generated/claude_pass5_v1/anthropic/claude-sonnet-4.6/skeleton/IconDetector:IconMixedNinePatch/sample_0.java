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

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

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

    /**
     * Map from base name (e.g. "icon") to the list of files that map to that name.
     * Key: resource name (without extension), Value: list of files found
     */
    private Map<String, List<File>> mFilesByName;

    public IconDetector() {
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFilesByName = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mFilesByName == null) {
            return;
        }

        for (Map.Entry<String, List<File>> entry : mFilesByName.entrySet()) {
            List<File> files = entry.getValue();
            if (files.size() < 2) {
                continue;
            }

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
                String message =
                        "The files `"
                                + pngFile.getName()
                                + "` and `"
                                + ninePatchFile.getName()
                                + "` both map to the same drawable resource id `@drawable/"
                                + resourceName
                                + "`; change the name of one of them";

                Location location = Location.create(pngFile);
                Location secondary = Location.create(ninePatchFile);
                secondary.setMessage("Also maps to: `@drawable/" + resourceName + "`");
                location.setSecondary(secondary);

                context.report(
                        new Incident(
                                ISSUE,
                                location,
                                message));
            }
        }

        mFilesByName = null;
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
    public void checkFolder(@NonNull Context context, @NonNull String folderName) {
        // Not used directly; we scan files in beforeCheckFile
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String fileName = file.getName();

        // Only interested in PNG files
        if (!fileName.endsWith(".png")) {
            return;
        }

        // Determine the resource name
        String baseName;
        if (fileName.endsWith(".9.png")) {
            // Nine-patch: "icon.9.png" -> "icon"
            baseName = fileName.substring(0, fileName.length() - ".9.png".length());
        } else {
            // Regular PNG: "icon.png" -> "icon"
            baseName = fileName.substring(0, fileName.length() - ".png".length());
        }

        if (mFilesByName == null) {
            mFilesByName = new HashMap<>();
        }

        List<File> fileList = mFilesByName.get(baseName);
        if (fileList == null) {
            fileList = new ArrayList<>();
            mFilesByName.put(baseName, fileList);
        }
        fileList.add(file);
    }

    // The following methods are required by the skeleton but are not used
    // for this particular detector which works at the file/folder level.

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }

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