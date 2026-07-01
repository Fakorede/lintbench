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
import java.util.Locale;
import java.util.Map;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high). " +
            "This lint check identifies icons which do not have complete coverage " +
            "across the densities.\n\n" +
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
    public void checkProject(@com.android.annotations.NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        List<File> resourceFolders = context.getProject().getResourceFolders();
        Map<String, IconInfo> icons = new HashMap<>();

        for (File resDir : resourceFolders) {
            File[] subdirs = resDir.listFiles();
            if (subdirs == null) {
                continue;
            }
            for (File subdir : subdirs) {
                if (!subdir.isDirectory()) {
                    continue;
                }
                String folderName = subdir.getName();
                String type = null;
                if (folderName.startsWith("drawable")) {
                    type = "drawable";
                } else if (folderName.startsWith("mipmap")) {
                    type = "mipmap";
                }
                if (type == null) {
                    continue;
                }

                String[] segments = folderName.split("-");
                String density = null;
                boolean isAnyDpiOrNoDpi = false;
                for (String segment : segments) {
                    if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
                        segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi")) {
                        density = segment;
                    } else if (segment.equals("anydpi") || segment.equals("nodpi")) {
                        isAnyDpiOrNoDpi = true;
                    }
                }

                File[] files = subdir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String fileName = file.getName();
                    int lastDot = fileName.lastIndexOf('.');
                    if (lastDot == -1) {
                        continue;
                    }
                    String name = fileName.substring(0, lastDot);
                    String ext = fileName.substring(lastDot + 1).toLowerCase(Locale.US);

                    String key = type + "/" + name;
                    IconInfo info = icons.get(key);
                    if (info == null) {
                        info = new IconInfo(type, name);
                        icons.put(key, info);
                    }

                    if (ext.equals("xml")) {
                        if (density == null || isAnyDpiOrNoDpi) {
                            info.hasXml = true;
                        }
                    } else if (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("webp")) {
                        if (density != null) {
                            info.densityFiles.put(density, file);
                        }
                    }
                }
            }
        }

        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (IconInfo info : icons.values()) {
            if (info.hasXml) {
                continue;
            }
            if (info.densityFiles.isEmpty()) {
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
            if ("mipmap".equals(info.type)) {
                required.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String req : required) {
                if (!info.densityFiles.containsKey(req)) {
                    missing.add(req);
                }
            }

            if (!missing.isEmpty()) {
                File reportFile = null;
                for (String req : required) {
                    if (info.densityFiles.containsKey(req)) {
                        reportFile = info.densityFiles.get(req);
                        break;
                    }
                }
                if (reportFile == null) {
                    reportFile = info.densityFiles.values().iterator().next();
                }

                Location location = Location.create(reportFile);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(missing.get(i));
                }
                String message = String.format("Missing the following densities in '%s/%s': %s", info.type, info.name, sb.toString());
                context.report(ISSUE, location, message);
            }
        }
    }

    private static class IconInfo {
        final String type;
        final String name;
        final Map<String, File> densityFiles = new HashMap<>();
        boolean hasXml = false;

        IconInfo(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }
}