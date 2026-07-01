package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

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
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, IconInfo> icons;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        icons = new HashMap<>();
    }

    @Override
    public void checkFolder(@NonNull ResourceFolderContext context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        String folderName = folder.getName();
        ResourceFolderType folderType = context.getFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        boolean isMipmap = folderType == ResourceFolderType.MIPMAP;
        String density = getDensity(folderName);

        for (File file : files) {
            String fileName = file.getName();
            if (file.isDirectory() || fileName.startsWith(".")) {
                continue;
            }

            int dotIndex = fileName.indexOf('.');
            String baseName = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
            String extension = dotIndex != -1 ? fileName.substring(dotIndex + 1).toLowerCase() : "";

            if (!extension.equals("png") && !extension.equals("jpg") && 
                !extension.equals("jpeg") && !extension.equals("gif") && 
                !extension.equals("webp") && !extension.equals("xml")) {
                continue;
            }

            String key = folderType.getName() + "/" + baseName;
            IconInfo info = icons.computeIfAbsent(key, k -> new IconInfo(baseName, isMipmap));

            if (extension.equals("xml")) {
                if (density == null || density.equals("anydpi") || density.equals("nodpi")) {
                    info.hasDefaultXml = true;
                }
            } else {
                if (density == null) {
                    info.hasDefaultBitmap = true;
                } else if (density.equals("nodpi")) {
                    info.hasNodpi = true;
                } else {
                    info.densityFiles.put(density, file);
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (icons == null || icons.isEmpty()) {
            return;
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (IconInfo info : icons.values()) {
            if (info.hasDefaultXml || info.hasNodpi) {
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

            if (info.isMipmap || info.name.startsWith("ic_launcher")) {
                required.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String density : required) {
                if (!info.densityFiles.containsKey(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                File existingFile = info.densityFiles.values().iterator().next();
                Location location = Location.create(existingFile);

                StringBuilder sb = new StringBuilder();
                sb.append("Icon '").append(info.name).append("' is missing the following densities: ");
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(missing.get(i));
                }

                context.report(ISSUE, location, sb.toString());
            }
        }
    }

    private String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
                segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi") ||
                segment.equals("nodpi") || segment.equals("anydpi")) {
                return segment;
            }
        }
        return null;
    }

    private static class IconInfo {
        final String name;
        final boolean isMipmap;
        boolean hasDefaultXml = false;
        boolean hasDefaultBitmap = false;
        boolean hasNodpi = false;
        final Map<String, File> densityFiles = new HashMap<>();

        IconInfo(String name, boolean isMipmap) {
            this.name = name;
            this.isMipmap = isMipmap;
        }
    }
}