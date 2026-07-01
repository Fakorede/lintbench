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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
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

public class IconDetector extends Detector implements Detector.ResourceFileScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, Scope.ALL_RESOURCES_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`,"
                            + " the image file and the nine patch file will both map to the same"
                            + " drawable resource, `@drawable/file`, which is probably not what"
                            + " was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Maps from drawable folder path to a map of base name -> list of files with that base name
    private final Map<String, Map<String, List<File>>> mFolderToNames = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderToNames.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, List<File>>> folderEntry : mFolderToNames.entrySet()) {
            Map<String, List<File>> nameMap = folderEntry.getValue();
            for (Map.Entry<String, List<File>> nameEntry : nameMap.entrySet()) {
                List<File> files = nameEntry.getValue();
                if (files.size() > 1) {
                    // Check if we have both a .png and a .9.png
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
                        String baseName = nameEntry.getKey();
                        Location location = Location.create(ninePatchFile);
                        Location secondary = Location.create(pngFile);
                        secondary.setMessage("This file clashes with the nine-patch file");
                        location.setSecondary(secondary);
                        context.report(
                                new Incident(
                                        ISSUE,
                                        location,
                                        String.format(
                                                "The files `%1$s.png` and `%1$s.9.png` both map"
                                                        + " to the same drawable resource"
                                                        + " `@drawable/%1$s`",
                                                baseName)));
                    }
                }
            }
        }
        mFolderToNames.clear();
    }

    @Override
    public void run(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();

        // Only interested in PNG files
        if (!name.endsWith(".png")) {
            return;
        }

        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        // Determine the base name (resource name)
        // file.9.png -> base name is "file"
        // file.png -> base name is "file"
        String baseName;
        if (name.endsWith(".9.png")) {
            baseName = name.substring(0, name.length() - ".9.png".length());
        } else {
            baseName = name.substring(0, name.length() - ".png".length());
        }

        String folderPath = folder.getPath();
        Map<String, List<File>> nameMap = mFolderToNames.get(folderPath);
        if (nameMap == null) {
            nameMap = new HashMap<>();
            mFolderToNames.put(folderPath, nameMap);
        }

        List<File> fileList = nameMap.get(baseName);
        if (fileList == null) {
            fileList = new ArrayList<>();
            nameMap.put(baseName, fileList);
        }
        fileList.add(file);
    }

    // The following methods are stubs required by the skeleton but not used for this check

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }
}