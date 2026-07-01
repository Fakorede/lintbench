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

    // Map from folder path -> map from base name -> list of files
    private final Map<String, Map<String, List<File>>> mFolderMap = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFolderMap.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, List<File>>> folderEntry : mFolderMap.entrySet()) {
            Map<String, List<File>> nameMap = folderEntry.getValue();
            for (Map.Entry<String, List<File>> entry : nameMap.entrySet()) {
                List<File> files = entry.getValue();
                if (files.size() >= 2) {
                    boolean hasPng = false;
                    boolean hasNinePatch = false;
                    File pngFile = null;
                    File ninePatchFile = null;
                    for (File f : files) {
                        String name = f.getName();
                        if (name.endsWith(".9.png")) {
                            hasNinePatch = true;
                            ninePatchFile = f;
                        } else if (name.endsWith(".png")) {
                            hasPng = true;
                            pngFile = f;
                        }
                    }
                    if (hasPng && hasNinePatch && pngFile != null && ninePatchFile != null) {
                        String message =
                                String.format(
                                        "The files `%1$s` and `%2$s` both map to the same"
                                                + " drawable resource `@drawable/%3$s`",
                                        pngFile.getName(),
                                        ninePatchFile.getName(),
                                        entry.getKey());
                        Location location = Location.create(pngFile);
                        Location secondary = Location.create(ninePatchFile);
                        secondary.setMessage("Nine-patch file here");
                        location.setSecondary(secondary);
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
        mFolderMap.clear();
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
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public void visitResourceFolder(
            @NonNull ResourceContext context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        String folderKey = folder.getPath();
        Map<String, List<File>> nameMap = mFolderMap.get(folderKey);
        if (nameMap == null) {
            nameMap = new HashMap<>();
            mFolderMap.put(folderKey, nameMap);
        }
        for (File file : files) {
            String name = file.getName();
            String baseName = getBaseName(name);
            if (baseName != null) {
                List<File> list = nameMap.get(baseName);
                if (list == null) {
                    list = new ArrayList<>();
                    nameMap.put(baseName, list);
                }
                list.add(file);
            }
        }
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        } else if (fileName.endsWith(".png")) {
            return fileName.substring(0, fileName.length() - ".png".length());
        }
        return null;
    }
}