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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
        "IconDensities",
        "Icon densities validation",
        "Icons will look best if a custom version is provided for each of the " +
        "major screen density classes (low, medium, high, extra high). This lint " +
        "check identifies icons which do not have complete coverage across the " +
        "densities.\n\n" +
        "Low density is not really used much anymore, so this check ignores " +
        "the ldpi density. To force lint to include it, set the environment " +
        "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on " +
        "current density usage, see " +
        "https://developer.android.com/about/dashboards",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.ALL_RESOURCES_SCOPE)
    );

    @Override
    public void checkProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        Map<String, ResourceInfo> resources = new HashMap<>();

        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File res : resourceFolders) {
            File[] subdirs = res.listFiles();
            if (subdirs == null) continue;
            for (File subdir : subdirs) {
                String subdirName = subdir.getName();
                if (!subdirName.startsWith("drawable")) {
                    continue;
                }

                File[] files = subdir.listFiles();
                if (files == null) continue;

                String density = getDensity(subdirName);
                boolean isDensityIndependent = "anydpi".equals(density) || "nodpi".equals(density);

                for (File file : files) {
                    String fileName = file.getName();
                    if (fileName.startsWith(".")) {
                        continue;
                    }
                    int dot = fileName.lastIndexOf('.');
                    if (dot == -1) {
                        continue;
                    }
                    String baseName = fileName.substring(0, dot);
                    String ext = fileName.substring(dot).toLowerCase();

                    ResourceInfo info = resources.get(baseName);
                    if (info == null) {
                        info = new ResourceInfo(baseName);
                        resources.put(baseName, info);
                    }

                    if (ext.equals(".xml")) {
                        if (density == null || isDensityIndependent) {
                            info.hasXmlFallback = true;
                        }
                    } else if (ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".webp")) {
                        if (density == null) {
                            info.hasDefaultBitmap = true;
                        } else if (!isDensityIndependent) {
                            info.densityFiles.put(density, file);
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        List<String> required = new ArrayList<>();
        if (includeLdpi) {
            required.add("ldpi");
        }
        required.add("mdpi");
        required.add("hdpi");
        required.add("xhdpi");
        required.add("xxhdpi");

        for (ResourceInfo info : resources.values()) {
            if (info.hasXmlFallback) {
                continue;
            }
            if (info.densityFiles.isEmpty()) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String density : required) {
                if (!info.densityFiles.containsKey(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                File file = null;
                for (String density : required) {
                    file = info.densityFiles.get(density);
                    if (file != null) {
                        break;
                    }
                }
                if (file == null) {
                    file = info.densityFiles.values().iterator().next();
                }

                Location location = Location.create(file);
                Collections.sort(missing);
                String message = String.format(
                    "Missing the following densities in %s: %s",
                    info.name, String.join(", ", missing)
                );
                context.report(ISSUE, location, message);
            }
        }
    }

    @Nullable
    private String getDensity(@NonNull String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") ||
                segment.equals("mdpi") ||
                segment.equals("hdpi") ||
                segment.equals("xhdpi") ||
                segment.equals("xxhdpi") ||
                segment.equals("xxxhdpi") ||
                segment.equals("nodpi") ||
                segment.equals("anydpi")) {
                return segment;
            }
        }
        return null;
    }

    private static class ResourceInfo {
        @NonNull final String name;
        boolean hasXmlFallback = false;
        boolean hasDefaultBitmap = false;
        @NonNull final Map<String, File> densityFiles = new HashMap<>();

        ResourceInfo(@NonNull String name) {
            this.name = name;
        }
    }
}