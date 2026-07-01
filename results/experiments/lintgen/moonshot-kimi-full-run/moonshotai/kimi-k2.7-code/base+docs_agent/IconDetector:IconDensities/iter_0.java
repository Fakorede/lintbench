package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class IconDetector extends Detector implements ResourceFolderScanner {

    private static final String INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final Set<String> REQUIRED_DENSITIES =
            new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi"));

    private static final Set<String> DENSITY_NAMES =
            new HashSet<>(Arrays.asList(
                    "ldpi", "mdpi", "hdpi", "xhdpi",
                    "xxhdpi", "xxxhdpi", "tvdpi", "nodpi", "anydpi"
            ));

    private static final Set<String> IMAGE_EXTENSIONS =
            new HashSet<>(Arrays.asList("png", "gif", "jpg", "jpeg", "webp", "bmp"));

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major "
                    + "screen density classes (low, medium, high, extra high). This check "
                    + "identifies icons which do not have complete coverage across the "
                    + "densities.\n\nLow density is not really used much anymore, so this "
                    + "check ignores the ldpi density. To force lint to include it, set the "
                    + "environment variable `ANDROID_LINT_INCLUDE_LDPI=true`.",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE),
            "https://developer.android.com/guide/practices/screens_support.html"
    );

    private Map<Project, Map<String, Map<String, IconEntry>>> mIcons;

    @Override
    @Nullable
    public EnumSet<ResourceFolderType> getApplicableFolders() {
        return EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIcons = new HashMap<>();
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull File folder) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE
                && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String density = getDensity(folder);
        if (density == null || "anydpi".equals(density) || "nodpi".equals(density)) {
            return;
        }

        Project project = context.getProject();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile() || !isImageFile(file)) {
                continue;
            }

            String baseName = getBaseName(file.getName());
            Map<String, Map<String, IconEntry>> projectMap =
                    mIcons.computeIfAbsent(project, k -> new HashMap<>());
            Map<String, IconEntry> typeMap =
                    projectMap.computeIfAbsent(folderType.getName(), k -> new HashMap<>());
            IconEntry entry = typeMap.computeIfAbsent(baseName, k -> new IconEntry());
            entry.densities.add(density);
            if (entry.location == null) {
                entry.location = Location.create(file);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        Map<String, Map<String, IconEntry>> projectMap = mIcons.get(project);
        if (projectMap == null) {
            return;
        }

        Set<String> required = new HashSet<>(REQUIRED_DENSITIES);
        if (isIncludeLdpi()) {
            required.add("ldpi");
        }

        for (Map.Entry<String, Map<String, IconEntry>> typeEntry : projectMap.entrySet()) {
            String folderType = typeEntry.getKey();
            for (Map.Entry<String, IconEntry> iconEntry : typeEntry.getValue().entrySet()) {
                IconEntry entry = iconEntry.getValue();
                Set<String> missing = new TreeSet<>(required);
                missing.removeAll(entry.densities);
                if (!missing.isEmpty()) {
                    String message = String.format(
                            "Icon `%1$s` is missing the following density versions in the `%2$s` folder: %3$s",
                            iconEntry.getKey(),
                            folderType,
                            String.join(", ", missing)
                    );
                    Location location = entry.location != null
                            ? entry.location
                            : Location.create(context.getProject().getDir());
                    context.report(ICON_DENSITIES, location, message);
                }
            }
        }

        mIcons.remove(project);
    }

    @Nullable
    private static String getDensity(@NonNull File folder) {
        String[] parts = folder.getName().split("-");
        for (int i = 1; i < parts.length; i++) {
            if (DENSITY_NAMES.contains(parts[i])) {
                return parts[i];
            }
        }
        return null;
    }

    private static boolean isImageFile(@NonNull File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".9.png")) {
            return true;
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1 || lastDot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(lastDot + 1);
        return IMAGE_EXTENSIONS.contains(ext);
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot != -1) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }

    private static boolean isIncludeLdpi() {
        return Boolean.parseBoolean(System.getProperty(INCLUDE_LDPI))
                || Boolean.parseBoolean(System.getenv(INCLUDE_LDPI));
    }

    private static class IconEntry {
        final Set<String> densities = new HashSet<>();
        Location location;
    }
}