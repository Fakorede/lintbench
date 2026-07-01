package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.ResourceFolderInfo;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IconDetector extends ResourceFolderDetector {

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high). " +
            "This lint check identifies icons which do not have complete coverage " +
            "across the densities.\n\n" +
            "Low density is not really used much anymore, so this check ignores " +
            "the ldpi density. To force lint to include it, set the environment " +
            "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
            "current density usage, see " +
            "https://developer.android.com/about/dashboards",
            Category.ICONS, 5, Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    private static final Set<String> BASE_DENSITIES = new HashSet<>(Arrays.asList(
            "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"));

    private final Map<String, Set<String>> mIconDensities = new HashMap<>();
    private final Map<String, File> mIconFiles = new HashMap<>();

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NotNull ResourceFolderInfo folder) {
        File dir = folder.getFolder();
        String name = dir.getName();
        if (!name.startsWith("drawable-")) {
            return;
        }

        String density = name.substring("drawable-".length());
        int dash = density.indexOf('-');
        if (dash != -1) {
            density = density.substring(0, dash);
        }

        if (!isTargetDensity(density)) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".xml") || fileName.endsWith(".9.png")) {
                continue;
            }
            String baseName = getBaseName(fileName);
            mIconDensities.computeIfAbsent(baseName, k -> new HashSet<>()).add(density);
            mIconFiles.putIfAbsent(baseName, file);
        }
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (mIconDensities.isEmpty()) {
            return;
        }

        Set<String> required = getRequiredDensities();

        for (Map.Entry<String, Set<String>> entry : mIconDensities.entrySet()) {
            String icon = entry.getKey();
            Set<String> present = entry.getValue();
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(present);

            if (!missing.isEmpty()) {
                File file = mIconFiles.get(icon);
                Location location = file != null ? Location.create(file) : Location.create(context.getProject().getDir());
                String message = String.format("Missing density variation folders for `%s`: %s",
                        icon, String.join(", ", missing));
                context.report(ISSUE, location, message);
            }
        }

        mIconDensities.clear();
        mIconFiles.clear();
    }

    private static boolean includeLdpi() {
        String val = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        return val != null && val.equalsIgnoreCase("true");
    }

    private static boolean isTargetDensity(String density) {
        return BASE_DENSITIES.contains(density) || (includeLdpi() && "ldpi".equals(density));
    }

    private static Set<String> getRequiredDensities() {
        Set<String> densities = new HashSet<>(BASE_DENSITIES);
        if (includeLdpi()) {
            densities.add("ldpi");
        }
        return densities;
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}