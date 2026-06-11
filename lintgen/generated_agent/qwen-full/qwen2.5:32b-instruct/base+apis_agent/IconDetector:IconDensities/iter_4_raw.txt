package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IconDetector extends ResourceXmlDetector {

    private static final String ANDROID_LINT_INCLUDE_LDPI = "ANDROID_LINT_INCLUDE_LDPI";

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icons will look best if a custom version is provided for each of the major screen density classes (low, medium, high, extra high). This lint check identifies icons which do not have complete coverage across the densities.",
            "Icons should be provided in multiple densities to ensure they look good on different devices. Missing densities can lead to pixelated or blurry icons.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Set<String> requiredDensities;

    public IconDetector() {
        boolean includeLdpi = Boolean.getBoolean(ANDROID_LINT_INCLUDE_LDPI);
        if (includeLdpi) {
            this.requiredDensities = new HashSet<>(Arrays.asList("ldpi", "mdpi", "hdpi", "xhdpi"));
        } else {
            this.requiredDensities = new HashSet<>(Arrays.asList("mdpi", "hdpi", "xhdpi"));
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.DRAWABLE == folderType;
    }

    @Override
    public void visitResource(XmlContext context, String resourceType, String name, int[] ids) {
        if (resourceType.equals("drawable")) {
            checkIconDensities(context, name);
        }
    }

    private void checkIconDensities(XmlContext context, String iconName) {
        Set<String> availableDensities = new HashSet<>();
        for (String density : requiredDensities) {
            if (!context.getFolder().getDensityQualifier(density).exists(iconName)) {
                availableDensities.add(density);
            }
        }

        if (availableDensities.size() < requiredDensities.size()) {
            context.report(ISSUE, context.getLocation(context.getFile()), "Icon '" + iconName + "' is missing densities: " +
                    String.join(", ", requiredDensities));
        }
    }
}