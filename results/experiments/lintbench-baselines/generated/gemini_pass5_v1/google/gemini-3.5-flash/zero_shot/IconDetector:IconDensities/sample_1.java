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
            "major screen density classes (low, medium, high, extra high). This lint " +
            "check identifies icons which do not have complete coverage across the " +
            "densities.\n\n" +
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

    @Override
    public void run(Context context) {
        if (!context.getProject().getReportIssues()) {
            return;
        }

        Map<String, Map<String, File>> drawables = new HashMap<>();

        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File resFolder : resourceFolders) {
            scanResFolder(resFolder, drawables);
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        for (Map.Entry<String, Map<String, File>> entry : drawables.entrySet()) {
            String resName = entry.getKey();
            Map<String, File> densityMap = entry.getValue();

            boolean hasDensitySpecific = false;
            boolean isMipmap = false;

            for (Map.Entry<String, File> densityEntry : densityMap.entrySet()) {
                String density = densityEntry.getKey();
                if (!density.equals("nodpi")) {
                    hasDensitySpecific = true;
                }
                File file = densityEntry.getValue();
                if (file != null && file.getParentFile().getName().startsWith("mipmap")) {
                    isMipmap = true;
                }
            }

            if (!hasDensitySpecific) {
                continue;
            }

            List<String> expected = new ArrayList<>();
            if (includeLdpi) {
                expected.add("ldpi");
            }
            expected.add("mdpi");
            expected.add("hdpi");
            expected.add("xhdpi");
            expected.add("xxhdpi");
            if (isMipmap) {
                expected.add("xxxhdpi");
            }

            List<String> missing = new ArrayList<>();
            for (String density : expected) {
                if (!densityMap.containsKey(density)) {
                    missing.add(density);
                }
            }

            if (!missing.isEmpty()) {
                File fileToReportOn = null;
                for (File file : densityMap.values()) {
                    if (file != null) {
                        fileToReportOn = file;
                        break;
                    }
                }
                if (fileToReportOn != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < missing.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(missing.get(i));
                    }
                    String message = String.format(
                            "Icon '%s' is missing the following densities: %s",
                            resName, sb.toString()
                    );
                    context.report(
                            ISSUE,
                            Location.create(fileToReportOn),
                            message
                    );
                }
            }
        }
    }

    private void scanResFolder(File resFolder, Map<String, Map<String, File>> drawables) {
        File[] subfolders = resFolder.listFiles();
        if (subfolders == null) {
            return;
        }
        for (File subfolder : subfolders) {
            if (subfolder.isDirectory()) {
                String folderName = subfolder.getName();
                if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                    String density = getDensity(folderName);
                    File[] files = subfolder.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        String fileName = file.getName();
                        if (isBitmap(fileName)) {
                            String resName = getResourceName(fileName);
                            Map<String, File> densityMap = drawables.get(resName);
                            if (densityMap == null) {
                                densityMap = new HashMap<>();
                                drawables.put(resName, densityMap);
                            }
                            densityMap.put(density, file);
                        }
                    }
                }
            }
        }
    }

    private String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (segment.equals("ldpi") ||
                segment.equals("mdpi") ||
                segment.equals("hdpi") ||
                segment.equals("xhdpi") ||
                segment.equals("xxhdpi") ||
                segment.equals("xxxhdpi")) {
                return segment;
            }
        }
        return "nodpi";
    }

    private boolean isBitmap(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    private String getResourceName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }
}