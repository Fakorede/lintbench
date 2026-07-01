package com.android.tools.lint.checks;

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
import java.util.Map;

public class IconDetector extends Detector {

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
            4,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void afterCheckEachProject(Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders.isEmpty()) {
            return;
        }

        Map<String, Map<String, File>> iconMap = new HashMap<>();

        for (File resDir : resourceFolders) {
            File[] subDirs = resDir.listFiles();
            if (subDirs == null) {
                continue;
            }
            for (File subDir : subDirs) {
                String dirName = subDir.getName();
                if (dirName.startsWith("drawable") || dirName.startsWith("mipmap")) {
                    String type = dirName.startsWith("drawable") ? "drawable" : "mipmap";
                    String density = null;
                    String[] segments = dirName.split("-");
                    for (String segment : segments) {
                        if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
                                segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi")) {
                            density = segment;
                            break;
                        }
                    }

                    File[] files = subDir.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        if (file.isDirectory()) {
                            continue;
                        }
                        String fileName = file.getName();
                        if (fileName.endsWith(".xml")) {
                            continue;
                        }
                        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || 
                                fileName.endsWith(".jpeg") || fileName.endsWith(".gif") || 
                                fileName.endsWith(".webp")) {
                            String baseName = fileName;
                            int dot = baseName.indexOf('.');
                            if (dot != -1) {
                                baseName = baseName.substring(0, dot);
                            }
                            String key = type + "/" + baseName;
                            Map<String, File> densities = iconMap.computeIfAbsent(key, k -> new HashMap<>());
                            if (density != null) {
                                densities.put(density, file);
                            } else {
                                densities.put("default", file);
                            }
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (Map.Entry<String, Map<String, File>> entry : iconMap.entrySet()) {
            String key = entry.getKey();
            Map<String, File> densities = entry.getValue();

            if (densities.size() == 1 && densities.containsKey("default")) {
                continue;
            }

            List<String> required = new ArrayList<>();
            if (includeLdpi) {
                required.add("ldpi");
            }
            required.add("mdpi");
            required.add("hdpi");
            required.add("xhdpi");
            required.add("xxhdpi");

            if (key.startsWith("mipmap/")) {
                required.add("xxxhdpi");
            }

            boolean hasDensitySpecific = false;
            for (String req : required) {
                if (densities.containsKey(req)) {
                    hasDensitySpecific = true;
                    break;
                }
            }

            if (!hasDensitySpecific) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String req : required) {
                if (!densities.containsKey(req)) {
                    missing.add(req);
                }
            }

            if (!missing.isEmpty()) {
                File reportFile = null;
                for (File f : densities.values()) {
                    if (f != null) {
                        reportFile = f;
                        break;
                    }
                }

                if (reportFile != null) {
                    Location location = Location.create(reportFile);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < missing.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(missing.get(i));
                    }
                    String message = String.format("Icon '%s' is missing the following densities: %s",
                            key, sb.toString());
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}