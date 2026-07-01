package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IconDetector extends Detector {

    public static final Implementation IMPLEMENTATION = new Implementation(
        IconDetector.class,
        Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
        "IconDensities",
        "Icon densities validation",
        "Icons will look best if a custom version is provided for each of the " +
        "major screen density classes (low, medium, high, extra high). This lint " +
        "check identifies icons which do not have complete coverage across the densities.\n\n" +
        "Low density is not really used much anymore, so this check ignores the " +
        "ldpi density. To force lint to include it, set the environment variable " +
        "`ANDROID_LINT_INCLUDE_LDPI=true`.",
        Category.ICONS,
        4,
        Severity.WARNING,
        IMPLEMENTATION
    );

    @Override
    public void run(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();
        Map<String, Map<String, File>> iconToDensityMap = new HashMap<>();

        for (File resFolder : resourceFolders) {
            File[] subdirs = resFolder.listFiles();
            if (subdirs == null) {
                continue;
            }
            for (File subdir : subdirs) {
                if (subdir.isDirectory() && subdir.getName().startsWith("drawable")) {
                    String density = getDensity(subdir.getName());
                    File[] files = subdir.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        if (file.isFile() && isImageFile(file.getName())) {
                            String filename = file.getName();
                            Map<String, File> densities = iconToDensityMap.get(filename);
                            if (densities == null) {
                                densities = new HashMap<>();
                                iconToDensityMap.put(filename, densities);
                            }
                            densities.put(density, file);
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, Map<String, File>> entry : iconToDensityMap.entrySet()) {
            String filename = entry.getKey();
            Map<String, File> densities = entry.getValue();

            boolean hasDensityVar = false;
            for (String d : densities.keySet()) {
                if (isDensitySpecific(d)) {
                    hasDensityVar = true;
                    break;
                }
            }
            if (!hasDensityVar) {
                continue;
            }

            boolean isLauncher = filename.startsWith("ic_launcher");
            List<String> expected = new ArrayList<>();
            if ("true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
                expected.add("ldpi");
            }
            expected.add("mdpi");
            expected.add("hdpi");
            expected.add("xhdpi");
            expected.add("xxhdpi");
            if (isLauncher) {
                expected.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String exp : expected) {
                if (!densities.containsKey(exp)) {
                    missing.add(exp);
                }
            }

            if (!missing.isEmpty()) {
                File file = densities.values().iterator().next();
                Location location = Location.create(file);
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(missing.get(i));
                }
                
                String message = String.format(
                    "Icon '%1$s' is missing the following densities: %2$s",
                    filename,
                    sb.toString()
                );
                context.report(ISSUE, location, message);
            }
        }
    }

    private static String getDensity(String folderName) {
        if (folderName.contains("-anydpi")) {
            return "anydpi";
        }
        if (folderName.contains("-nodpi")) {
            return "nodpi";
        }
        if (folderName.contains("-xxxhdpi")) {
            return "xxxhdpi";
        }
        if (folderName.contains("-xxhdpi")) {
            return "xxhdpi";
        }
        if (folderName.contains("-xhdpi")) {
            return "xhdpi";
        }
        if (folderName.contains("-hdpi")) {
            return "hdpi";
        }
        if (folderName.contains("-mdpi")) {
            return "mdpi";
        }
        if (folderName.contains("-ldpi")) {
            return "ldpi";
        }
        if (folderName.contains("-tvdpi")) {
            return "tvdpi";
        }
        return "default";
    }

    private static boolean isDensitySpecific(String density) {
        return "ldpi".equals(density) || "mdpi".equals(density) || "hdpi".equals(density) ||
               "xhdpi".equals(density) || "xxhdpi".equals(density) || "xxxhdpi".equals(density);
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }
}