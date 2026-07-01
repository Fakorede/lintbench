package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
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

    private static class IconData {
        final Set<Density> densities = EnumSet.noneOf(Density.class);
        File sampleFile;
    }

    private final Map<String, IconData> mIcons = new HashMap<>();

    @Override
    public void checkFolder(Context context, File folder) {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folder.getName());
        if (config == null) {
            return;
        }

        ResourceFolderType type = config.getFolderType();
        if (type != ResourceFolderType.DRAWABLE && type != ResourceFolderType.MIPMAP) {
            return;
        }

        Density density = config.getDensityQualifier() != null
                ? config.getDensityQualifier().getValue()
                : null;
        if (density == null) {
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
            if (!isImageExtension(ext)) {
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

    @Override
    public void afterCheckEachProject(Context context) {
        boolean includeLdpi = "true".equalsIgnoreCase(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));

        EnumSet<Density> required = EnumSet.of(
                Density.MEDIUM,
                Density.HIGH,
                Density.XHIGH,
                Density.XXHIGH,
                Density.XXXHIGH
        );
        if (includeLdpi) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, IconData> entry : mIcons.entrySet()) {
            String baseName = entry.getKey();
            IconData data = entry.getValue();

            EnumSet<Density> missing = required.clone();
            missing.removeAll(data.densities);

            if (!missing.isEmpty()) {
                List<String> missingNames = new ArrayList<>();
                for (Density d : missing) {
                    missingNames.add(d.getResourceValue());
                }
                Collections.sort(missingNames);

                String message = String.format(
                        "Missing density variations for icon `%s`: missing %s",
                        baseName,
                        String.join(", ", missingNames)
                );

                Location location = Location.create(data.sampleFile);
                context.report(ISSUE, location, message);
            }
        }
        mIcons.clear();
    }

    private static boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("webp") || ext.equals("gif")
                || ext.equals("jpg") || ext.equals("jpeg");
    }
}