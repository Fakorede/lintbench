package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    public static final Issue ICON_DENSITIES = Issue.create(
            "IconDensities",
            "Icon density validation",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.\n\n"
                    + "Low density is not really used much anymore, so this check ignores the ldpi density. To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see https://developer.android.com/about/dashboards",
            Category.ICONS,
            3,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    private final Map<Project, ProjectState> mStates = new HashMap<>();

    private static class ProjectState {
        final Map<String, Density> folderDensities = new HashMap<>();
        final Map<String, Map<Density, File>> icons = new HashMap<>();
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mStates.put(context.getProject(), new ProjectState());
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        Project project = context.getProject();
        ProjectState state = mStates.remove(project);
        if (state == null) {
            return;
        }

        Set<Density> relevant = EnumSet.noneOf(Density.class);
        for (Density density : state.folderDensities.values()) {
            if (density == Density.LOW) {
                if (includeLdpi()) {
                    relevant.add(density);
                }
            } else if (density != Density.NODPI && density != Density.ANYDPI && density != Density.TVDPI) {
                relevant.add(density);
            }
        }

        if (relevant.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Map<Density, File>> entry : state.icons.entrySet()) {
            String baseName = entry.getKey();
            Map<Density, File> present = entry.getValue();

            Set<Density> missing = EnumSet.copyOf(relevant);
            missing.removeAll(present.keySet());

            if (!missing.isEmpty()) {
                List<String> missingNames = new ArrayList<>();
                for (Density density : missing) {
                    missingNames.add(density.getResourceValue());
                }
                Collections.sort(missingNames);

                File first = present.values().iterator().next();
                String message = String.format(
                        "Missing density variation: the icon `%1$s` is missing the following density variations: %2$s",
                        baseName, join(missingNames));

                context.report(ICON_DENSITIES, Location.create(first), message);
            }
        }
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        ResourceFolderType type = ResourceFolderType.getFolderType(folderName);
        if (type != ResourceFolderType.DRAWABLE) {
            return;
        }

        FolderConfiguration config = FolderConfiguration.getConfig(folderName);
        if (config == null) {
            return;
        }

        DensityQualifier densityQualifier = config.getDensityQualifier();
        if (densityQualifier == null) {
            return;
        }

        Density density = densityQualifier.getValue();
        if (density == null) {
            return;
        }

        ProjectState state = mStates.get(context.getProject());
        if (state == null) {
            return;
        }

        state.folderDensities.put(folderName, density);

        File[] files = context.getDir().listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            if (name.startsWith(".")) {
                continue;
            }

            String extension = getExtension(name);
            if (extension == null || !isImageExtension(extension)) {
                continue;
            }

            String baseName = getBaseName(name);
            Map<Density, File> map = state.icons.get(baseName);
            if (map == null) {
                map = new HashMap<>();
                state.icons.put(baseName, map);
            }
            map.put(density, file);
        }
    }

    private static boolean includeLdpi() {
        String value = System.getenv(INCLUDE_LDPI);
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1"));
    }

    private static String join(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static String getExtension(@NonNull String name) {
        if (name.endsWith(".9.png")) {
            return ".9.png";
        }
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(index) : null;
    }

    private static String getBaseName(@NonNull String name) {
        String extension = getExtension(name);
        if (extension == null) {
            return name;
        }
        return name.substring(0, name.length() - extension.length());
    }

    private static boolean isImageExtension(@NonNull String extension) {
        return extension.equalsIgnoreCase(".png")
                || extension.equalsIgnoreCase(".9.png")
                || extension.equalsIgnoreCase(".jpg")
                || extension.equalsIgnoreCase(".jpeg")
                || extension.equalsIgnoreCase(".gif")
                || extension.equalsIgnoreCase(".webp");
    }
}