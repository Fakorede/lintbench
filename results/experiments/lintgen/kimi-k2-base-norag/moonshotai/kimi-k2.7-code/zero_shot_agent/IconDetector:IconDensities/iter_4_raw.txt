package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String ENV_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final List<String> DENSITY_QUALIFIERS = Arrays.asList(
            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "tvdpi"
    );

    private static final List<String> REQUIRED_DENSITIES = Arrays.asList(
            "mdpi", "hdpi", "xhdpi"
    );

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE
    );

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards\n\n"
                    + "Reference documentation: https://developer.android.com/guide/practices/screens_support.html",
            Category.ICONS,
            4,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private Map<String, Set<String>> mSeenDensities;
    private Map<String, File> mReferenceFiles;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mSeenDensities = new HashMap<>();
        mReferenceFiles = new HashMap<>();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mSeenDensities == null || mSeenDensities.isEmpty()) {
            return;
        }

        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv(ENV_INCLUDE_LDPI));
        List<String> required = new ArrayList<>(REQUIRED_DENSITIES);
        if (includeLdpi) {
            required.add(0, "ldpi");
        }

        for (Map.Entry<String, Set<String>> entry : mSeenDensities.entrySet()) {
            String baseName = entry.getKey();
            Set<String> seen = entry.getValue();
            if (seen.size() <= 1) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String density : required) {
                if (!seen.contains(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                File reference = mReferenceFiles.get(baseName);
                if (reference != null) {
                    String message = missing.size() == 1
                            ? "The following density is not covered: " + missing.get(0)
                            : "The following densities are not covered: " + join(missing);
                    context.report(ICON_DENSITIES, Location.create(reference), message);
                }
            }
        }
    }

    @Override
    public void checkFolder(@NonNull Context context, @NonNull File folder) {
        checkFolderImpl(context, folder);
    }

    public void checkFolder(@NonNull ResourceContext context, @NonNull File folder) {
        checkFolderImpl(context, folder);
    }

    private void checkFolderImpl(@NonNull Context context, @NonNull File folder) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        String density = getDensityQualifier(folder.getName());
        if (density == null) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String name = file.getName();
            if (!isBitmap(name)) {
                continue;
            }

            String baseName = getBaseName(name);
            Set<String> seen = mSeenDensities.get(baseName);
            if (seen == null) {
                seen = new HashSet<>();
                mSeenDensities.put(baseName, seen);
                mReferenceFiles.put(baseName, file);
            }
            seen.add(density);
        }
    }

    @Nullable
    private static String getDensityQualifier(@NonNull String folderName) {
        int dash = folderName.indexOf('-');
        if (dash == -1 || dash == folderName.length() - 1) {
            return null;
        }
        String[] parts = folderName.substring(dash + 1).split("-");
        for (String part : parts) {
            if (DENSITY_QUALIFIERS.contains(part)) {
                return part;
            }
        }
        return null;
    }

    private static boolean isBitmap(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    @NonNull
    private static String join(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}