package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ICON_DENSITIES = Issue.create(
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
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FOLDER_SCOPE
            )
    );

    @Override
    public void checkProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        List<File> resFolders = context.getProject().getResourceFolders();
        if (resFolders.isEmpty()) {
            return;
        }

        Map<String, Map<String, File>> groupToDensities = new HashMap<>();
        Map<String, Boolean> groupToHasFallbackXml = new HashMap<>();

        for (File res : resFolders) {
            File[] subfolders = res.listFiles();
            if (subfolders == null) {
                continue;
            }
            for (File subfolder : subfolders) {
                String subfolderName = subfolder.getName();
                String folderType;
                if (subfolderName.startsWith("drawable")) {
                    folderType = "drawable";
                } else if (subfolderName.startsWith("mipmap")) {
                    folderType = "mipmap";
                } else {
                    continue;
                }

                String density = getDensity(subfolderName);
                if (density == null) {
                    density = "default";
                }

                File[] files = subfolder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String fileName = file.getName();
                    if (fileName.startsWith(".")) {
                        continue;
                    }

                    String lower = fileName.toLowerCase(Locale.US);
                    if (!lower.endsWith(".png") && !lower.endsWith(".webp") &&
                        !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") &&
                        !lower.endsWith(".xml")) {
                        continue;
                    }

                    String baseName = getBaseName(fileName);
                    String groupKey = folderType + ":" + baseName;

                    if (fileName.endsWith(".xml")) {
                        if ("default".equals(density) || "anydpi".equals(density) || "nodpi".equals(density)) {
                            groupToHasFallbackXml.put(groupKey, true);
                        }
                    }

                    Map<String, File> densities = groupToDensities.computeIfAbsent(groupKey, k -> new HashMap<>());
                    densities.put(density, file);
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (Map.Entry<String, Map<String, File>> entry : groupToDensities.entrySet()) {
            String groupKey = entry.getKey();
            Map<String, File> densities = entry.getValue();

            if (Boolean.TRUE.equals(groupToHasFallbackXml.get(groupKey))) {
                continue;
            }

            boolean hasDensitySpecific = false;
            for (String d : densities.keySet()) {
                if (!"default".equals(d) && !"anydpi".equals(d) && !"nodpi".equals(d)) {
                    hasDensitySpecific = true;
                    break;
                }
            }

            if (!hasDensitySpecific) {
                continue;
            }

            int colon = groupKey.indexOf(':');
            String folderType = groupKey.substring(0, colon);
            String baseName = groupKey.substring(colon + 1);

            List<String> required = new ArrayList<>();
            if (includeLdpi) {
                required.add("ldpi");
            }
            required.add("mdpi");
            required.add("hdpi");
            required.add("xhdpi");
            required.add("xxhdpi");
            if ("mipmap".equals(folderType)) {
                required.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String req : required) {
                if (!densities.containsKey(req)) {
                    missing.add(req);
                }
            }

            if (!missing.isEmpty()) {
                File reportFile = null;
                for (Map.Entry<String, File> densityEntry : densities.entrySet()) {
                    String d = densityEntry.getKey();
                    if (!"default".equals(d) && !"anydpi".equals(d) && !"nodpi".equals(d)) {
                        reportFile = densityEntry.getValue();
                        break;
                    }
                }
                if (reportFile == null) {
                    reportFile = densities.values().iterator().next();
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Icon '").append(baseName).append("' ");
                sb.append("does not have complete coverage across the densities; missing ");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        if (i == missing.size() - 1) {
                            sb.append(" and ");
                        } else {
                            sb.append(", ");
                        }
                    }
                    sb.append(missing.get(i));
                }
                sb.append(".");

                context.report(ICON_DENSITIES, Location.create(reportFile), sb.toString());
            }
        }
    }

    @Nullable
    private static String getDensity(@NonNull String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if ("ldpi".equals(segment) || "mdpi".equals(segment) || "hdpi".equals(segment) ||
                "xhdpi".equals(segment) || "xxhdpi".equals(segment) || "xxxhdpi".equals(segment) ||
                "nodpi".equals(segment) || "anydpi".equals(segment)) {
                return segment;
            }
        }
        return null;
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.indexOf('.');
        if (dot != -1) {
            String base = fileName.substring(0, dot);
            return base.isEmpty() ? fileName : base;
        }
        return fileName;
    }
}