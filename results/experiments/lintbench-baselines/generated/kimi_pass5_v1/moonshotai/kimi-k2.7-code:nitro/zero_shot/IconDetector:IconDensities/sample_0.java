package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final boolean INCLUDE_LDPI =
            Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen "
                    + "density classes (low, medium, high, extra high). This lint check "
                    + "identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi "
                    + "density. To force lint to include it, set the environment variable "
                    + "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density "
                    + "usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            3,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Set<File> mProcessedParents = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull File folder) {
        File parent = folder.getParentFile();
        if (parent == null || !mProcessedParents.add(parent)) {
            return;
        }

        String folderName = folder.getName();
        ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
        if (type == null) {
            return;
        }
        String prefix = type == ResourceFolderType.DRAWABLE ? "drawable" : "mipmap";

        File[] children = parent.listFiles();
        if (children == null) {
            return;
        }

        Map<String, List<File>> filesByBaseName = new HashMap<>();
        Map<String, EnumSet<Density>> densitiesByBaseName = new HashMap<>();

        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            String name = child.getName();
            if (!name.startsWith(prefix)) {
                continue;
            }

            Density density = getDensity(name);
            if (density == Density.NODPI
                    || density == Density.ANYDPI
                    || density == Density.TVDPI) {
                continue;
            }
            if (density == null) {
                density = Density.MEDIUM;
            }

            File[] files = child.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                String fileName = file.getName();
                if (!isImageFile(fileName)) {
                    continue;
                }
                String baseName = getBaseName(fileName);
                if (baseName == null) {
                    continue;
                }

                densitiesByBaseName
                        .computeIfAbsent(baseName, k -> EnumSet.noneOf(Density.class))
                        .add(density);
                filesByBaseName.computeIfAbsent(baseName, k -> new ArrayList<>()).add(file);
            }
        }

        EnumSet<Density> required = EnumSet.of(Density.MEDIUM, Density.HIGH, Density.XHIGH);
        if (INCLUDE_LDPI) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, EnumSet<Density>> entry : densitiesByBaseName.entrySet()) {
            String baseName = entry.getKey();
            EnumSet<Density> present = entry.getValue();
            if (present.containsAll(required)) {
                continue;
            }

            EnumSet<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(present);

            List<File> files = filesByBaseName.get(baseName);
            if (files == null || files.isEmpty()) {
                continue;
            }
            Location location = Location.create(files.get(0));
            StringBuilder sb = new StringBuilder();
            for (Density density : missing) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(density.getResourceValue());
            }
            String message = String.format(
                    "The icon %s is missing density %s",
                    baseName,
                    sb.toString());

            context.report(ISSUE, location, message);
        }
    }

    @Nullable
    private static Density getDensity(@NonNull String folderName) {
        String[] parts = folderName.split("-");
        for (String part : parts) {
            Density density = Density.getEnum(part);
            if (density != null) {
                return density;
            }
        }
        return null;
    }

    @Nullable
    private static String getBaseName(@NonNull String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot);
        }
        return null;
    }

    private static boolean isImageFile(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".9.png");
    }
}