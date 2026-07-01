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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon missing common density",
            "Icons will look best if a custom version is provided for each of the major "
                    + "screen density classes (low, medium, high, extra high). This lint check "
                    + "identifies icons which do not have complete coverage across the "
                    + "densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the "
                    + "ldpi density. To force lint to include it, set the environment variable "
                    + "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density "
                    + "usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private static final String[] DENSITIES = {
            "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    private static final String[] DENSITIES_WITH_LDPI = {
            "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"
    };

    private static final boolean INCLUDE_LDPI =
            Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

    private final Map<String, Map<String, File>> mIcons = new HashMap<>();

    @Override
    public void beforeCheckEachProject(Context context) {
        mIcons.clear();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        File folder = context.getFolder();
        if (folder == null || !folder.isDirectory()) {
            return;
        }

        String density = getDensityQualifier(folderName);
        if (density == null || !isRelevantDensity(density)) {
            return;
        }

        String folderType = getFolderType(folderName);

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory() || !isImageFile(file.getName())) {
                continue;
            }

            String key = folderType + "/" + file.getName();

            Map<String, File> densities = mIcons.get(key);
            if (densities == null) {
                densities = new HashMap<>();
                mIcons.put(key, densities);
            }
            densities.put(density, file);
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        String[] required = INCLUDE_LDPI ? DENSITIES_WITH_LDPI : DENSITIES;

        for (Map.Entry<String, Map<String, File>> entry : mIcons.entrySet()) {
            Map<String, File> densityToFile = entry.getValue();
            if (densityToFile.isEmpty()) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String density : required) {
                if (!densityToFile.containsKey(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                File file = densityToFile.values().iterator().next();

                String name = entry.getKey().substring(entry.getKey().indexOf('/') + 1);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < required.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(required[i]);
                }

                String message = String.format(
                        "The icon `%1$s` is not present in all densities [%2$s]",
                        name, sb.toString());

                context.report(ICON_DENSITIES, Location.create(file), message);
            }
        }

        mIcons.clear();
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp");
    }

    private static String getDensityQualifier(String folderName) {
        if (!folderName.contains("-")) {
            return "mdpi";
        }

        String[] parts = folderName.split("-");
        for (String part : parts) {
            if (isDensityQualifier(part)) {
                return part;
            }
        }
        return null;
    }

    private static boolean isDensityQualifier(String qualifier) {
        for (String density : DENSITIES_WITH_LDPI) {
            if (density.equals(qualifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRelevantDensity(String density) {
        for (String d : DENSITIES) {
            if (d.equals(density)) {
                return true;
            }
        }
        return INCLUDE_LDPI && "ldpi".equals(density);
    }

    private static String getFolderType(String folderName) {
        int dash = folderName.indexOf('-');
        return dash == -1 ? folderName : folderName.substring(0, dash);
    }
}