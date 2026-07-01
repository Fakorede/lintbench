package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String ENV_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final String LDPI = "ldpi";
    private static final String MDPI = "mdpi";
    private static final String HDPI = "hdpi";
    private static final String XHDPI = "xhdpi";

    private static final List<String> DENSITY_ORDER =
            Arrays.asList(LDPI, MDPI, HDPI, XHDPI);
    private static final List<String> REQUIRED_DENSITIES =
            Arrays.asList(MDPI, HDPI, XHDPI);
    private static final List<String> REQUIRED_DENSITIES_WITH_LDPI =
            Arrays.asList(LDPI, MDPI, HDPI, XHDPI);

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon density coverage",
            "Icons will look best if a custom version is provided for each of the major "
                    + "screen density classes (low, medium, high, extra high). This lint check "
                    + "identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi "
                    + "density. To force lint to include it, set the environment variable "
                    + "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density "
                    + "usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            IMPLEMENTATION
    );

    private Map<String, Map<String, File>> mIcons;
    private Set<String> mWildcardIcons;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mIcons = new HashMap<>();
        mWildcardIcons = new HashSet<>();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mIcons == null || mIcons.isEmpty()) {
            return;
        }

        boolean includeLdpi = Boolean.parseBoolean(System.getenv(ENV_INCLUDE_LDPI));
        Set<String> required = new HashSet<>(
                includeLdpi ? REQUIRED_DENSITIES_WITH_LDPI : REQUIRED_DENSITIES);

        for (Map.Entry<String, Map<String, File>> entry : mIcons.entrySet()) {
            String key = entry.getKey();
            if (mWildcardIcons.contains(key)) {
                continue;
            }

            Map<String, File> densities = entry.getValue();
            if (densities.isEmpty()) {
                continue;
            }

            Set<String> missing = new HashSet<>(required);
            missing.removeAll(densities.keySet());
            if (missing.isEmpty()) {
                continue;
            }

            List<String> sortedMissing = new ArrayList<>(missing);
            Collections.sort(sortedMissing, new Comparator<String>() {
                @Override
                public int compare(String d1, String d2) {
                    return Integer.compare(
                            DENSITY_ORDER.indexOf(d1),
                            DENSITY_ORDER.indexOf(d2));
                }
            });

            File locationFile = densities.values().iterator().next();
            String name = key.substring(key.indexOf('/') + 1);
            String message = String.format(
                    Locale.US,
                    "The icon '%s' is missing density variations: %s",
                    name,
                    join(sortedMissing));

            context.report(ISSUE, context.getLocation(locationFile), message);
        }

        mIcons = null;
        mWildcardIcons = null;
    }

    @Override
    public void checkDir(@NonNull ResourceContext context, @NonNull File folder) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        String density = getDensityQualifier(folder, folderType);
        if (density == null) {
            return;
        }

        boolean isWildcard = "any".equals(density);

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile() || file.isHidden()) {
                continue;
            }

            String name = file.getName();
            if (!isImageFile(name)) {
                continue;
            }

            String key = folderType.getName() + "/" + name;
            if (isWildcard) {
                mWildcardIcons.add(key);
                continue;
            }

            Map<String, File> densityMap = mIcons.get(key);
            if (densityMap == null) {
                densityMap = new HashMap<>();
                mIcons.put(key, densityMap);
            }
            densityMap.put(density, file);
        }
    }

    private static String getDensityQualifier(File folder, ResourceFolderType folderType) {
        String folderName = folder.getName();
        String prefix = folderType.getName() + "-";
        if (!folderName.startsWith(prefix)) {
            return null;
        }

        String[] qualifiers = folderName.substring(prefix.length()).split("-");
        for (String qualifier : qualifiers) {
            if ("anydpi".equals(qualifier) || "nodpi".equals(qualifier)) {
                return "any";
            }

            Density d = Density.getEnum(qualifier);
            if (d != null) {
                return qualifier;
            }
        }

        return null;
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".9.png")
                || lower.endsWith(".webp")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif");
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}