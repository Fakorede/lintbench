package com.android.tools.lint.checks;

import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final String EXPLANATION =
            "Icons will look best if a custom version is provided for each of the major screen "
                    + "density classes (low, medium, high, extra high). This lint check identifies "
                    + "icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi "
                    + "density. To force lint to include it, set the environment variable "
                    + "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density "
                    + "usage, see https://developer.android.com/about/dashboards";

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            EXPLANATION,
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<ResourceFolderType, Map<String, Map<Density, File>>> mIcons =
            new ConcurrentHashMap<>();

    @Override
    public List<Issue> getIssues() {
        return Collections.singletonList(ISSUE);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mIcons.clear();
    }

    @Override
    public void visitResourceFile(ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();
        if (!isIconFile(fileName)) {
            return;
        }

        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }

        ResourceFolderType type = ResourceFolderType.getFolderType(folder.getName());
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        FolderConfiguration config = FolderConfiguration.getConfig(folder.getName());
        if (config == null) {
            return;
        }

        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null) {
            return;
        }

        Density density = densityQualifier.getValue();
        if (density == null
                || density == Density.NODPI
                || density == Density.ANYDPI) {
            return;
        }

        String baseName = getBaseName(fileName);
        Map<String, Map<Density, File>> typeMap =
                mIcons.computeIfAbsent(type, k -> new ConcurrentHashMap<>());
        Map<Density, File> densityMap =
                typeMap.computeIfAbsent(baseName, k -> new ConcurrentHashMap<>());
        densityMap.put(density, file);
    }

    @Override
    public void afterCheckRootProject(Context context) {
        EnumSet<Density> expected = EnumSet.of(
                Density.MEDIUM,
                Density.HIGH,
                Density.XHIGH,
                Density.XXHIGH,
                Density.XXXHIGH
        );
        if (Boolean.parseBoolean(System.getenv(INCLUDE_LDPI))) {
            expected.add(Density.LOW);
        }

        for (Map.Entry<ResourceFolderType, Map<String, Map<Density, File>>> typeEntry
                : mIcons.entrySet()) {
            ResourceFolderType type = typeEntry.getKey();
            String typeName = type.getName();

            for (Map.Entry<String, Map<Density, File>> iconEntry
                    : typeEntry.getValue().entrySet()) {
                String baseName = iconEntry.getKey();
                Map<Density, File> densities = iconEntry.getValue();
                if (densities.isEmpty()) {
                    continue;
                }

                boolean hasExpectedDensity = false;
                for (Density density : densities.keySet()) {
                    if (expected.contains(density)) {
                        hasExpectedDensity = true;
                        break;
                    }
                }
                if (!hasExpectedDensity) {
                    continue;
                }

                File representative = null;
                for (File candidate : densities.values()) {
                    if (representative == null
                            || candidate.getPath().compareTo(representative.getPath()) < 0) {
                        representative = candidate;
                    }
                }
                if (representative == null) {
                    continue;
                }

                List<String> missingFolders = new ArrayList<>();
                for (Density density : expected) {
                    if (!densities.containsKey(density)) {
                        missingFolders.add(typeName + "-" + density.getResourceValue());
                    }
                }
                if (missingFolders.isEmpty()) {
                    continue;
                }

                String folderName = representative.getParentFile().getName();
                String message = "The image `res/" + folderName + "/"
                        + representative.getName()
                        + "` is missing density variations for the following density folders: "
                        + String.join(", ", missingFolders);

                Location location = Location.create(representative);
                context.report(ISSUE, location, message);
            }
        }
    }

    private static boolean isIconFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".xml");
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}