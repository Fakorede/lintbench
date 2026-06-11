package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "IconDensityValidation",
            "Checks that all icons provided in multiple densities compute to roughly the same density-independent pixel (dip) size.",
            "This catches errors where images are either placed in the wrong folder, or icons are changed to new sizes but some folders are forgotten.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Collections.emptySet())
    );

    @Override
    public List<Issue> getApplicableIssues() {
        return Collections.singletonList(ISSUE);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr != null && nameAttr.getValue().startsWith("icon_")) {
            checkIconDensity(context, element);
        }
    }

    private void checkIconDensity(XmlContext context, Element element) {
        String iconName = element.getAttribute("name");

        // Check all density folders for the icon
        int[] densities = {160, 240, 320, 480};
        float[] dipSizes = new float[densities.length];
        boolean first = true;
        float referenceDipSize = -1;

        for (int i = 0; i < densities.length; i++) {
            String densityFolder = "drawable-" + getDensitySuffix(densities[i]);
            if (context.getFolderType() == ResourceFolderType.DRAWABLE && context.getResourceName().equals(iconName)) {
                float dipSize = calculateDipSize(context, element, densities[i]);
                dipSizes[i] = dipSize;

                if (first) {
                    referenceDipSize = dipSize;
                    first = false;
                } else {
                    if (!isCloseEnough(dipSize, referenceDipSize)) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Icon size in density folder " + densityFolder +
                                        " does not match the reference size.");
                    }
                }
            }
        }
    }

    private String getDensitySuffix(int dpi) {
        switch (dpi) {
            case 160:
                return "mdpi";
            case 240:
                return "hdpi";
            case 320:
                return "xhdpi";
            case 480:
                return "xxhdpi";
            default:
                return "";
        }
    }

    private float calculateDipSize(XmlContext context, Element element, int dpi) {
        // Placeholder for actual dip size calculation logic
        // This should be replaced with the actual logic to compute the dip size.
        return 48f; // Example value
    }

    private boolean isCloseEnough(float a, float b) {
        final float epsilon = 0.1f;
        return Math.abs(a - b) <= epsilon;
    }
}