package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String ENV_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final String LDPI = "ldpi";
    private static final String MDPI = "mdpi";
    private static final String HDPI = "hdpi";
    private static final String XHDPI = "xhdpi";
    private static final String XXHDPI = "xxhdpi";
    private static final String XXXHDPI = "xxxhdpi";

    private static final Set<String> DENSITY_QUALIFIERS =
            new HashSet<>(Arrays.asList(LDPI, MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI));

    private static final List<String> DENSITY_ORDER =
            Arrays.asList(LDPI, MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI);

    private static final Set<String> DEFAULT_REQUIRED_DENSITIES =
            new HashSet<>(Arrays.asList(MDPI, HDPI, XHDPI));

    private static final String EXPLANATION =
            "Icons will look best if a custom version is provided for each of the major "
                    + "screen density classes (low, medium, high, extra high). This lint check "
                    + "identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the "
                    + "ldpi density. To force lint to include it, set the environment variable "
                    + "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density "
                    + "usage, see https://developer.android.com/about/dashboards";

    public static final Issue ICON_DENSITIES =
            Issue.create(
                    "IconDensities",
                    "Icon density validation",
                    EXPLANATION,
                    Category.ICONS,
                    4,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE),
                    "https://developer.android.com/guide/practices/screens_support.html");

    private final Map<String, IconEntry> mIcons = new HashMap<>();
    private Set<String> mRequiredDensities;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mIcons.clear();
        mRequiredDensities = getRequiredDensities();
    }

    @NotNull
    private Set<String> getRequiredDensities() {
        Set<String> required = new HashSet<>(DEFAULT_REQUIRED_DENSITIES);
        if (Boolean.parseBoolean(System.getenv(ENV_INCLUDE_LDPI))) {
            required.add(LDPI);
        }
        return required;
    }

    @Override
    @Nullable
    public List<ResourceFolderType> getApplicableFolders() {
        return Arrays.asList(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
    }

    @Override
    public void checkResourceFolder(@NotNull ResourceContext context, @NotNull File folder) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String density = getDensityQualifier(folder.getName());
        boolean hasDensityQualifier = density != null;

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile() || !isImageFile(file)) {
                continue;
            }

            String baseName = getBaseName(file.getName());
            String key = folderType.getName() + "/" + baseName;
            IconEntry entry = mIcons.get(key);
            if (entry == null) {
                entry = new IconEntry(folderType, baseName);
                mIcons.put(key, entry);
            }
            entry.files.add(file);
            if (hasDensityQualifier && DENSITY_QUALIFIERS.contains(density)) {
                entry.foundDensities.add(density);
            }
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (IconEntry entry : mIcons.values()) {
            if (entry.foundDensities.isEmpty()) {
                continue;
            }

            Set<String> missing = new HashSet<>(mRequiredDensities);
            missing.removeAll(entry.foundDensities);
            if (missing.isEmpty()) {
                continue;
            }

            File file = entry.files.get(0);
            String message =
                    String.format(
                            "Missing density variations of icon `%1$s` (found: %2$s; missing: %3$s)",
                            entry.baseName,
                            formatDensities(entry.foundDensities),
                            formatDensities(missing));

            context.report(ICON_DENSITIES, Location.create(file), message);
        }
    }

    @Nullable
    private static String getDensityQualifier(@NotNull String folderName) {
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            if (DENSITY_QUALIFIERS.contains(parts[i])) {
                return parts[i];
            }
        }
        return null;
    }

    private static boolean isImageFile(@NotNull File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".9.png")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
    }

    @NotNull
    private static String getBaseName(@NotNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - 6);
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    @NotNull
    private static String formatDensities(@NotNull Set<String> densities) {
        List<String> sorted = new ArrayList<>(densities);
        Collections.sort(
                sorted,
                new Comparator<String>() {
                    @Override
                    public int compare(@NotNull String a, @NotNull String b) {
                        int indexA = DENSITY_ORDER.indexOf(a);
                        int indexB = DENSITY_ORDER.indexOf(b);
                        if (indexA == -1) {
                            indexA = Integer.MAX_VALUE;
                        }
                        if (indexB == -1) {
                            indexB = Integer.MAX_VALUE;
                        }
                        return Integer.compare(indexA, indexB);
                    }
                });
        return String.join(", ", sorted);
    }

    private static final class IconEntry {
        final ResourceFolderType folderType;
        final String baseName;
        final Set<String> foundDensities = new HashSet<>();
        final List<File> files = new ArrayList<>();

        IconEntry(ResourceFolderType folderType, String baseName) {
            this.folderType = folderType;
            this.baseName = baseName;
        }
    }
}