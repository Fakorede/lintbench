package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.ResourceQualifier;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;

import java.io.File;
import java.util.*;

public class IconDetector extends Detector implements BinaryResourceScanner {

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
            new Implementation(IconDetector.class, Scope.ALL_RESOURCES_SCOPE)
    );

    private static final String ENV_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";
    private static final String[] BASE_REQUIRED_DENSITIES = {
            "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"
    };

    private Map<String, IconInfo> iconMap;
    private boolean includeLdpi;

    private static class IconInfo {
        final Set<String> densities = new HashSet<>();
        File firstFile;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        iconMap = new HashMap<>();
        includeLdpi = Boolean.parseBoolean(System.getenv(ENV_INCLUDE_LDPI));
    }

    @NonNull
    @Override
    public EnumSet<FileType> getApplicableFileTypes() {
        return EnumSet.of(FileType.IMAGE);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        ResourceType type = context.getResourceType();
        if (type != ResourceType.DRAWABLE && type != ResourceType.MIPMAP) {
            return;
        }

        File file = context.getFile();
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }

        String density = getDensity(context);
        if (density == null) {
            return;
        }

        IconInfo info = iconMap.computeIfAbsent(name, k -> new IconInfo());
        info.densities.add(density);
        if (info.firstFile == null) {
            info.firstFile = file;
        }
    }

    private static String getDensity(@NonNull ResourceContext context) {
        List<ResourceQualifier> qualifiers = context.getQualifiers();
        if (qualifiers != null) {
            for (ResourceQualifier q : qualifiers) {
                if (q instanceof DensityQualifier) {
                    Density d = ((DensityQualifier) q).getValue();
                    if (d != null) {
                        return d.getResourceValue();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (iconMap == null || iconMap.isEmpty()) {
            return;
        }

        List<String> required = new ArrayList<>(Arrays.asList(BASE_REQUIRED_DENSITIES));
        if (includeLdpi) {
            required.add("ldpi");
        }

        for (Map.Entry<String, IconInfo> entry : iconMap.entrySet()) {
            String name = entry.getKey();
            IconInfo info = entry.getValue();

            Set<String> missing = new HashSet<>(required);
            missing.removeAll(info.densities);

            if (!missing.isEmpty()) {
                List<String> sortedMissing = new ArrayList<>(missing);
                Collections.sort(sortedMissing);

                String message = String.format(
                        "Missing the following densities for icon %1$s: %2$s",
                        name,
                        String.join(", ", sortedMissing)
                );

                Location location = Location.create(info.firstFile);
                context.report(ISSUE, location, message);
            }
        }
        iconMap = null;
    }
}