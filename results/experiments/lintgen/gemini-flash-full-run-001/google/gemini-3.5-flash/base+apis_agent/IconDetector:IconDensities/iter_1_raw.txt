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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
            Scope.RESOURCE_FOLDER_SCOPE
        )
    );

    private final Map<String, IconInfo> icons = new HashMap<>();

    private static class IconInfo {
        final String name;
        final String folderType;
        final Set<String> densities = new HashSet<>();
        final Map<String, File> files = new HashMap<>();

        IconInfo(String name, String folderType) {
            this.name = name;
            this.folderType = folderType;
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(Context context) {
        icons.clear();
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        File folder = context.file;
        String density = getDensity(folderName);
        if (density == null) return;

        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) continue;
            String name = file.getName();
            if (isBitmap(name)) {
                String baseName = getBaseName(name);
                String folderType = folderName.startsWith("drawable") ? "drawable" : "mipmap";
                String key = folderType + ":" + baseName;
                IconInfo info = icons.get(key);
                if (info == null) {
                    info = new IconInfo(baseName, folderType);
                    icons.put(key, info);
                }
                info.densities.add(density);
                info.files.put(density, file);
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        reportIssues(context);
        icons.clear();
    }

    private void reportIssues(Context context) {
        for (IconInfo info : icons.values()) {
            boolean isMipmap = info.folderType.equals("mipmap");
            List<String> required = new ArrayList<>();
            if ("true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"))) {
                required.add("ldpi");
            }
            required.add("mdpi");
            required.add("hdpi");
            required.add("xhdpi");
            required.add("xxhdpi");
            if (isMipmap) {
                required.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String req : required) {
                if (!info.densities.contains(req)) {
                    missing.add(req);
                }
            }

            if (!missing.isEmpty()) {
                File reportFile = null;
                for (String density : required) {
                    if (info.files.containsKey(density)) {
                        reportFile = info.files.get(density);
                        break;
                    }
                }
                if (reportFile == null) {
                    for (File f : info.files.values()) {
                        if (f != null) {
                            reportFile = f;
                            break;
                        }
                    }
                }

                if (reportFile != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < missing.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(missing.get(i));
                    }
                    String missingStr = sb.toString();
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