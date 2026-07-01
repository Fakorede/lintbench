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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NonNull;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

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

    private static class IconInfo {
        final String name;
        final Set<String> densities = new HashSet<>();
        final Map<String, File> files = new HashMap<>();

        IconInfo(String name) {
            this.name = name;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        Map<String, IconInfo> icons = new HashMap<>();

        for (File resDir : resourceFolders) {
            File[] subDirs = resDir.listFiles();
            if (subDirs == null) continue;
            for (File subDir : subDirs) {
                if (!subDir.isDirectory()) continue;
                String folderName = subDir.getName();
                String folderType = getFolderType(folderName);
                if (folderType == null) continue;
                String density = getDensity(folderName);
                if (density == null) continue;

                File[] files = subDir.listFiles();
                if (files == null) continue;
                for (File file : files) {
                    if (file.isDirectory()) continue;
                    String name = file.getName();
                    if (isBitmap(name)) {
                        String baseName = getBaseName(name);
                        String key = folderType + ":" + baseName;
                        IconInfo info = icons.get(key);
                        if (info == null) {
                            info = new IconInfo(baseName);
                            icons.put(key, info);
                        }
                        info.densities.add(density);
                        info.files.put(density, file);
                    }
                }
            }
        }

        List<String> required = new ArrayList<>();
        if ("true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
            required.add("ldpi");
        }
        required.add("mdpi");
        required.add("hdpi");
        required.add("xhdpi");
        required.add("xxhdpi");

        for (IconInfo info : icons.values()) {
            List<String> missing = new ArrayList<>();
            for (String req : required) {
                if (!info.densities.contains(req)) {
                    missing.add(req);
                }
            }
            if (!missing.isEmpty()) {
                File reportFile = null;
                for (File f : info.files.values()) {
                    if (f != null) {
                        reportFile = f;
                        break;
                    }
                }
                if (reportFile != null) {
                    String missingStr = String.join(", ", missing);
                    String message = String.format(
                        "Icon '%s' is missing densities: %s",
                        info.name, missingStr
                    );
                    context.report(ISSUE, Location.create(reportFile), message);
                }
            }
        }
    }

    private static String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
                segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi")) {
                return segment;
            }
        }
        return null;
    }

    private static String getFolderType(String folderName) {
        if (folderName.startsWith("drawable")) {
            return "drawable";
        } else if (folderName.startsWith("mipmap")) {
            return "mipmap";
        }
        return null;
    }

    private static boolean isBitmap(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static String getBaseName(String name) {
        int index = name.lastIndexOf('.');
        if (index != -1) {
            return name.substring(0, index);
        }
        return name;
    }
}