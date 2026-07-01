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

    // Maps from drawable resource name (without extension) to the list of files that map to it
    private Map<String, List<File>> mDrawableMap;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDrawableMap = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (mDrawableMap == null) {
            return;
        }

        for (Map.Entry<String, List<File>> entry : mDrawableMap.entrySet()) {
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
                    secondary.setMessage("This file clashes with the nine-patch file");
                    location.setSecondary(secondary);

                    String message =
                            String.format(
                                    "The files `%1$s` and `%2$s` both map to the same drawable "
                                            + "resource id `@drawable/%3$s`",
                                    ninePatchFile.getName(),
                                    pngFile.getName(),
                                    resourceName);

                    context.report(ISSUE, location, message);
                }
            }
        }

        mDrawableMap = null;
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

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mDrawableMap == null) {
            return;
        }

        File file = context.file;
        String fileName = file.getName();

        if (!fileName.endsWith(".png")) {
            return;
        }

        // Check if it's in a drawable or mipmap folder
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        // Determine the resource name
        String resourceName;
        if (fileName.endsWith(".9.png")) {
            // Nine-patch: strip the .9.png extension
            resourceName = fileName.substring(0, fileName.length() - ".9.png".length());
        } else {
            // Regular PNG: strip the .png extension
            resourceName = fileName.substring(0, fileName.length() - ".png".length());
        }

        List<File> fileList = mDrawableMap.get(resourceName);
        if (fileList == null) {
            fileList = new ArrayList<>();
            mDrawableMap.put(resourceName, fileList);
        }

        // Only add if not already present (avoid duplicates from multiple configurations)
        boolean found = false;
        for (File existing : fileList) {
            if (existing.getName().equals(fileName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            fileList.add(file);
        }
    }

    // The following methods are stubs required by the skeleton but not needed for this detector

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
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

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used
    }
}