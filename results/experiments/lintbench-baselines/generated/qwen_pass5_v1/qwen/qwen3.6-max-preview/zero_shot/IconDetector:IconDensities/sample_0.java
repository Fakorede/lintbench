package com.android.tools.lint.checks;

import com.android.resources.Density;
import com.android.resources.DensityQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceQualifier;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {
    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the " +
            "major screen density classes (low, medium, high, extra high). This lint check " +
            "identifies icons which do not have complete coverage across the densities.\n\n" +
            "Low density is not really used much anymore, so this check ignores the ldpi " +
            "density. To force lint to include it, set the environment variable " +
            "`ANDROID_LINT_INCLUDE_LDPI=true`. For more information on current density usage, see " +
            "https://developer.android.com/about/dashboards",
            Category.ICONS,
            4,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    private final Map<String, List<ResourceFile>> mIcons = new HashMap<>();

    @Nullable
    @Override
    public EnumSet<ResourceFolderType> getApplicableFolderTypes() {
        return EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
    }

    @Override
    public void visitFolder(@NotNull FolderContext context) {
        for (ResourceFile file : context.getFiles()) {
            String name = file.getName();
            // Skip XML files (vectors, selectors, etc.) as they are density-independent
            if (name.endsWith(".xml")) {
                continue;
            }
            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            mIcons.computeIfAbsent(baseName, k -> new ArrayList<>()).add(file);
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        boolean includeLdpi = "true".equals(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
        Set<Density> required = EnumSet.noneOf(Density.class);
        required.add(Density.MEDIUM);
        required.add(Density.HIGH);
        required.add(Density.XHIGH);
        required.add(Density.XXHIGH);
        required.add(Density.XXXHIGH);
        if (includeLdpi) {
            required.add(Density.LOW);
        }

        for (Map.Entry<String, List<ResourceFile>> entry : mIcons.entrySet()) {
            Set<Density> present = EnumSet.noneOf(Density.class);
            ResourceFile representative = null;
            boolean isDensityIndependent = false;

            for (ResourceFile rf : entry.getValue()) {
                if (representative == null) {
                    representative = rf;
                }
                boolean hasDensityQualifier = false;
                for (ResourceQualifier q : rf.getQualifiers()) {
                    if (q instanceof DensityQualifier) {
                        Density d = ((DensityQualifier) q).getValue();
                        if (d != null) {
                            if (d == Density.NODPI || d == Density.ANYDPI) {
                                isDensityIndependent = true;
                            } else {
                                present.add(d);
                            }
                            hasDensityQualifier = true;
                        }
                    }
                }
                // Default folder (no density qualifier) implies mdpi
                if (!hasDensityQualifier) {
                    present.add(Density.MEDIUM);
                }
            }

            // Skip icons that are explicitly density independent
            if (isDensityIndependent) {
                continue;
            }

            Set<Density> missing = EnumSet.copyOf(required);
            missing.removeAll(present);

            if (!missing.isEmpty() && representative != null) {
                String missingStr = missing.stream()
                        .map(Density::getResourceValue)
                        .collect(Collectors.joining(", "));
                String message = String.format("Missing the following drawable densities: %s", missingStr);
                File file = representative.getFile();
                context.report(ISSUE, Location.create(file), message);
            }
        }
        mIcons.clear();
    }
}