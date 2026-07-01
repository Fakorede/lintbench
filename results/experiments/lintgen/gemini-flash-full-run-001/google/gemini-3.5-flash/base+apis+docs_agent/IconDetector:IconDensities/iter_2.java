package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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
            Scope.RESOURCE_FOLDER_SCOPE
        )
    );

    private final Map<Project, Map<String, Map<String, File>>> projectToIcons = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        Project project = context.getProject();
        if (!project.getReportIssues()) {
            return;
        }

        String density = getDensity(folderName);
        if (density == null) {
            return;
        }

        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, Map<String, File>> iconMap = projectToIcons.get(project);
        if (iconMap == null) {
            iconMap = new HashMap<>();
            projectToIcons.put(project, iconMap);
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

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        Map<String, Map<String, File>> iconMap = projectToIcons.remove(project);
        if (iconMap == null || iconMap.isEmpty()) {
            return;
        }

        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        Collection<String> resConfigs = project.getResourceConfigurations();
        boolean hasDensityConfig = false;
        if (resConfigs != null && !resConfigs.isEmpty()) {
            for (String config : resConfigs) {
                if (isDensity(config)) {
                    hasDensityConfig = true;
                    break;
                }
            }
        }

        for (Map.Entry<String, Map<String, File>> entry : iconMap.entrySet()) {
            Map<String, File> densities = entry.getValue();

            boolean isMipmap = false;
            for (File file : densities.values()) {
                File parent = file.getParentFile();
                if (parent != null && parent.getName().startsWith("mipmap")) {
                    isMipmap = true;
                    break;
                }
            }

            List<String> expectedDensities = new ArrayList<>();
            if (includeLdpi) {
                expectedDensities.add("ldpi");
            }
            expectedDensities.add("mdpi");
            expectedDensities.add("hdpi");
            expectedDensities.add("xhdpi");
            expectedDensities.add("xxhdpi");
            if (isMipmap) {
                expectedDensities.add("xxxhdpi");
            }

            if (hasDensityConfig && resConfigs != null) {
                List<String> filtered = new ArrayList<>();
                for (String d : expectedDensities) {
                    if (resConfigs.contains(d)) {
                        filtered.add(d);
                    }
                }
                expectedDensities = filtered;
            }

            boolean hasExpectedDensity = false;
            for (String d : expectedDensities) {
                if (densities.containsKey(d)) {
                    hasExpectedDensity = true;
                    break;
                }
            }
            if (!hasExpectedDensity) {
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

                List<File> sortedFiles = new ArrayList<>(densities.values());
                sortedFiles.sort(File::compareTo);
                Location location = null;
                Location last = null;
                for (File file : sortedFiles) {
                    Location fileLocation = Location.create(file);
                    if (location == null) {
                        location = fileLocation;
                        last = location;
                    } else {
                        last.setSecondary(fileLocation);
                        last = fileLocation;
                    }
                }

                File reportFolder = reportFile.getParentFile();
                String message = String.format(
                    "Missing the following densities in %1$s: %2$s",
                    reportFolder.getName(),
                    describeList(missing, false)
                );

                context.report(ISSUE, location, message);
            }
        }
    }

    private static String describeList(List<String> list, boolean useAnd) {
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int size = list.size();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                if (i == size - 1) {
                    sb.append(useAnd ? " and " : " or ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private String getDensity(String folderName) {
        String[] segments = folderName.split("-");
        for (String segment : segments) {
            if (isDensity(segment)) {
                return segment;
            }
        }
        return null;
    }

    private boolean isDensity(String segment) {
        return segment.equals("ldpi") || segment.equals("mdpi") || segment.equals("hdpi") ||
               segment.equals("xhdpi") || segment.equals("xxhdpi") || segment.equals("xxxhdpi");
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