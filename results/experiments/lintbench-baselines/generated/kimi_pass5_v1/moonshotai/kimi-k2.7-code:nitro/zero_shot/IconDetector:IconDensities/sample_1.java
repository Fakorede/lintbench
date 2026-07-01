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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements Detector.ResourceFolderDetector {

    private static final String INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    private static final String ISSUE_ID = "IconDensities";

    private static final Issue ICON_DENSITIES = Issue.create(
            ISSUE_ID,
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen "
                    + "density classes (low, medium, high, extra high). This check identifies "
                    + "icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the "
                    + "ldpi density. To force lint to include it, set the environment variable "
                    + "`ANDROID_LINT_INCLUDE_LDPI=true`.",
            Category.ICONS,
            3,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final List<Density> RELEVANT_DENSITIES = Arrays.asList(
            Density.LDPI,
            Density.MDPI,
            Density.HDPI,
            Density.XHIGH,
            Density.XXHIGH,
            Density.XXXHIGH
    );

    private final Map<String, Set<Density>> mNameToDensities = new HashMap<>();
    private final Map<String, File> mNameToExampleFile = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mNameToDensities.clear();
        mNameToExampleFile.clear();
    }

    @Override
    public void checkFolder(ResourceContext context) {
        File folder = context.getFolder();
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folder.getName());
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
                || density == Density.ANYDPI
                || density == Density.TVDPI
                || density == Density.UNKNOWN) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            if (!isImageFile(name)) {
                continue;
            }

            String baseName = getBaseName(name);
            String key = folderType.getName() + "/" + baseName;

            Set<Density> densities = mNameToDensities.get(key);
            if (densities == null) {
                densities = EnumSet.noneOf(Density.class);
                mNameToDensities.put(key, densities);
                mNameToExampleFile.put(key, file);
            }
            densities.add(density);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        boolean includeLdpi = Boolean.parseBoolean(System.getenv(INCLUDE_LDPI));

        for (Map.Entry<String, Set<Density>> entry : mNameToDensities.entrySet()) {
            Set<Density> densities = entry.getValue();

            List<Density> present = new ArrayList<>();
            for (Density density : RELEVANT_DENSITIES) {
                if (densities.contains(density) && (density != Density.LDPI || includeLdpi)) {
                    present.add(density);
                }
            }

            if (present.size() < 2) {
                continue;
            }

            int minIndex = RELEVANT_DENSITIES.indexOf(present.get(0));
            int maxIndex = RELEVANT_DENSITIES.indexOf(present.get(present.size() - 1));

            List<String> missing = new ArrayList<>();
            for (int i = minIndex; i <= maxIndex; i++) {
                Density expected = RELEVANT_DENSITIES.get(i);
                if (expected == Density.LDPI && !includeLdpi) {
                    continue;
                }
                if (!densities.contains(expected)) {
                    missing.add(expected.getResourceValue());
                }
            }

            if (!missing.isEmpty()) {
                File file = mNameToExampleFile.get(entry.getKey());
                String baseName = getBaseName(file.getName());
                String message = String.format(
                        Locale.US,
                        "The icon `%1$s` is missing density variations: %2$s",
                        baseName,
                        join(missing));
                context.report(ICON_DENSITIES, Location.create(file), message);
            }
        }
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(i == items.size() - 1 ? " and " : ", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static boolean isImageFile(String name) {
        return hasSuffix(name, ".png")
                || hasSuffix(name, ".jpg")
                || hasSuffix(name, ".jpeg")
                || hasSuffix(name, ".gif")
                || hasSuffix(name, ".webp");
    }

    private static String getBaseName(String name) {
        if (hasSuffix(name, ".9.png")) {
            return name.substring(0, name.length() - 6);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name;
    }

    private static boolean hasSuffix(String name, String suffix) {
        return name.length() >= suffix.length()
                && name.regionMatches(true, name.length() - suffix.length(), suffix, 0, suffix.length());
    }
}