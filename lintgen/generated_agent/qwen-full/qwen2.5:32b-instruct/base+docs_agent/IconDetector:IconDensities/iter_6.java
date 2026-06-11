package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;

import org.jetbrains.uast.UElement;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class IconDetector extends ResourceXmlDetector {

    private static final String ISSUE_NAME = "IconDensities";
    private static final String SHORT_DESCRIPTION = "Missing icon densities";

    public static final Issue ISSUE = Issue.create(
            ISSUE_NAME,
            "Icons should be provided for each major screen density classes (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).",
            "Icons will look best if a custom version is provided for each of the major screen density classes. This lint check identifies icons which do not have complete coverage across the densities.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final ImmutableSet<Density> REQUIRED_DENSITIES = ImmutableSet.of(Density.MDPI, Density.HDPI, Density.XHDPI, Density.XXHDPI, Density.XXXHDPI);

    @Override
    public void visitResource(@NonNull PsiDirectory resDir, @NonNull Project project) {
        Set<String> iconNames = new HashSet<>();
        for (Density density : REQUIRED_DENSITIES) {
            String densityName = density.getName();
            if (!"ldpi".equals(densityName) || includeLdpi()) {
                PsiDirectory densityDir = resDir.findSubdirectory(SdkConstants.FD_RES + "-" + densityName);
                if (densityDir != null) {
                    for (String mipmapDir : new String[]{"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"}) {
                        PsiDirectory iconDir = densityDir.findSubdirectory(mipmapDir);
                        if (iconDir != null) {
                            Set<String> namesInDensity = getIconNames(iconDir);
                            if (!namesInDensity.isEmpty()) {
                                iconNames.addAll(namesInDensity);
                            }
                        }
                    }
                }
            }
        }

        for (String iconName : iconNames) {
            checkIconCoverage(resDir, iconName);
        }
    }

    private void checkIconCoverage(@NonNull PsiDirectory resDir, @NonNull String iconName) {
        Set<Pair<Density, Boolean>> densities = new HashSet<>();
        for (Density density : REQUIRED_DENSITIES) {
            String densityName = density.getName();
            if (!"ldpi".equals(densityName) || includeLdpi()) {
                PsiDirectory densityDir = resDir.findSubdirectory(SdkConstants.FD_RES + "-" + densityName);
                if (densityDir != null) {
                    for (String mipmapDir : new String[]{"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"}) {
                        PsiDirectory iconDir = densityDir.findSubdirectory(mipmapDir);
                        if (iconDir != null && hasIcon(iconDir, iconName)) {
                            densities.add(Pair.of(density, true));
                            break;
                        }
                    }
                } else {
                    densities.add(Pair.of(density, false));
                }
            }
        }

        Set<Density> missingDensities = densities.stream()
                .filter(pair -> !pair.second)
                .map(Pair::first)
                .collect(Collectors.toSet());

        if (!missingDensities.isEmpty()) {
            String message = "Icon '" + iconName + "' is missing in the following densities: " +
                    missingDensities.stream().map(Density::getName).collect(Collectors.joining(", "));
            report(ISSUE, resDir.getVirtualFile(), message);
        }
    }

    private boolean hasIcon(@NonNull PsiDirectory iconDir, @NonNull String iconName) {
        for (String mipmapDir : new String[]{"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"}) {
            if (iconDir.findSubdirectory(mipmapDir).findFile(iconName + ".png") != null) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getIconNames(@NonNull PsiDirectory iconDir) {
        Set<String> names = new HashSet<>();
        for (String mipmapDir : new String[]{"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"}) {
            PsiDirectory dir = iconDir.findSubdirectory(mipmapDir);
            if (dir != null) {
                for (PsiFile file : dir.getFiles()) {
                    String fileName = file.getName();
                    String nameWithoutExtension = fileName.substring(0, fileName.lastIndexOf('.'));
                    names.add(nameWithoutExtension);
                }
            }
        }
        return names;
    }

    private boolean includeLdpi() {
        return System.getenv("ANDROID_LINT_INCLUDE_LDPI") != null && Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
    }
}