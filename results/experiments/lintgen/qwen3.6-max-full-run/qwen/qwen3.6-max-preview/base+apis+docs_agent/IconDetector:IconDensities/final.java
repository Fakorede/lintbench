package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements ResourceFolderScanner {

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
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private static final Set<String> REQUIRED_DENSITIES = new HashSet<>(Arrays.asList(
            "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    ));

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "webp", "gif", "jpg", "jpeg"
    ));

    private final Map<String, IconData> mIcons = new HashMap<>();

    private static class IconData {
        final Set<String> densities = new HashSet<>();
        File sampleFile;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        String density = extractDensity(folderName);
        if (density == null) {
            return;
        }

        File folder = context.file;
        if (folder == null || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot == -1) {
                continue;
            }
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            if (!IMAGE_EXTENSIONS.contains(ext)) {
                continue;
            }

            String baseName = name.substring(0, dot);
            IconData data = mIcons.computeIfAbsent(baseName, k -> new IconData());
            data.densities.add(density);
            if (data.sampleFile == null) {
                data.sampleFile = file;
            }
        }
    }

    private String extractDensity(String folderName) {
        String[] parts = folderName.split("-");
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (REQUIRED_DENSITIES.contains(part) || part.equals("ldpi")) {
                return part;
            }
        }
        return null;
    }

    @Override
    public void afterCheckProject(Context context) {
        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        Set<String> required = new HashSet<>(REQUIRED_DENSITIES);
        if (includeLdpi) {
            required.add("ldpi");
        }

        for (Map.Entry<String, IconData> entry : mIcons.entrySet()) {
            String baseName = entry.getKey();
            IconData data = entry.getValue();

            Set<String> missing = new HashSet<>(required);
            missing.removeAll(data.densities);

            if (!missing.isEmpty()) {
                List<String> missingList = new ArrayList<>(missing);
                Collections.sort(missingList);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < missingList.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(missingList.get(i));
                }

                String message = String.format(
                        "Missing density variations for icon `%s`: missing %s",
                        baseName,
                        sb.toString()
                );

                Location location = Location.create(data.sampleFile);
                context.report(ISSUE, location, message);
            }
        }
        mIcons.clear();
    }
}