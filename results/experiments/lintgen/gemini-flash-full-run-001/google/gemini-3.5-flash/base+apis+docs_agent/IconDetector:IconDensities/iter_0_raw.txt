package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
        "IconDensities",
        "Icon densities validation",
        "Icons will look best if a custom version is provided for each of the " +
        "major screen density classes (low, medium, high, extra high). This " +
        "lint check identifies icons which do not have complete coverage " +
        "across the densities.\n\n" +
        "Low density is not really used much anymore, so this check ignores " +
        "the ldpi density. To force lint to include it, set the environment " +
        "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
        "current density usage, see " +
        "https://developer.android.com/about/dashboards",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        Map<String, Map<String, File>> iconMap = new HashMap<>();
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resFolder : resourceFolders) {
            File[] subfolders = resFolder.listFiles();
            if (subfolders == null) {
                continue;
            }
            for (File subfolder : subfolders) {
                String name = subfolder.getName();
                if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                    String density = getDensity(name);
                    if (density == null) {
                        continue;
                    }

                    File[] files = subfolder.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        String fileName = file.getName();
                        if (isIconFile(fileName)) {
                            String baseName = getBaseName(fileName);
                            Map<String, File> densities = iconMap.get(baseName);
                            if (densities == null) {
                                densities = new HashMap<>();
                                iconMap.put(baseName, densities);
                            }
                            densities.put(density, file);
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        List<String> expectedDensities = new ArrayList<>();
        if (includeLdpi) {
            expectedDensities.add("ldpi");
        }
        expectedDensities.add("mdpi");
        expectedDensities.add("hdpi");
        expectedDensities.add("xhdpi");
        expectedDensities.add("xxhdpi");
        expectedDensities.add("xxxhdpi");

        for (Map.Entry<String, Map<String, File>> entry : iconMap.entrySet()) {
            String iconName = entry.getKey();
            Map<String, File> densities = entry.getValue();

            boolean hasDensity = false;
            for (String d : expectedDensities) {
                if (densities.containsKey(d)) {
                    hasDensity = true;
                    break;
                }
            }
            if (!hasDensity) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String d : expectedDensities) {
                if (!densities.containsKey(d)) {
                    missing.add(d);
                }
            }

            if (!missing.isEmpty()) {
                File reportFile = null;
                for (String d : expectedDensities) {
                    if (densities.containsKey(d)) {
                        reportFile = densities.get(d);
                        break;
                    }
                }
                if (reportFile == null) {
                    reportFile = densities.values().iterator().next();
                }

                StringBuilder missingStr = new StringBuilder();
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        missingStr.append(", ");
                    }
                    missingStr.append(missing.get(i));
                }

                String message = String.format(
                    "Icon `%1$s` is missing the following densities: %2$s",
                    iconName, missingStr.toString()
                );

                Location location = Location.create(reportFile);
                context.report(ISSUE, location, message);
            }
        }
    }

    private String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
                segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi")) {
                return segment;
            }
        }
        return null;
    }

    private boolean isIconFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - 6);
        }
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }
}