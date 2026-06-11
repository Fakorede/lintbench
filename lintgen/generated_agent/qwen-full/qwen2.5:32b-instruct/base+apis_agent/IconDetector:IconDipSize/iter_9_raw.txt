package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "IconDensityValidation",
            "Checks that all icons provided in multiple densities compute to roughly the same density-independent pixel (dip) size.",
            "This check ensures that images are placed in the correct folder and have consistent sizes across different densities.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name != null && name.startsWith("ic_")) { // Assuming icons start with "ic_"
            checkIconDensity(context, element);
        }
    }

    private void checkIconDensity(XmlContext context, Element element) {
        int[] densities = new int[]{120, 160, 240, 320, 480}; // Common density buckets
        float[] dipSizes = new float[densities.length];
        boolean first = true;
        float referenceDipSize = -1;

        for (int i = 0; i < densities.length; i++) {
            String densityFolder = "drawable-" + getDensityString(densities[i]);
            Element iconElement = findIconInDensityFolder(context, element.getAttribute("name"), densityFolder);
            if (iconElement != null) {
                int sizeInPixels = Integer.parseInt(iconElement.getAttribute("size")); // Assuming there is a "size" attribute
                float dipSize = convertToDip(sizeInPixels, densities[i]);
                dipSizes[i] = dipSize;

                if (first) {
                    referenceDipSize = dipSize;
                    first = false;
                } else {
                    if (!isCloseEnough(dipSize, referenceDipSize)) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Icon size in density folder " + densityFolder + " is not consistent with other densities.");
                    }
                }
            }
        }
    }

    private Element findIconInDensityFolder(XmlContext context, String iconName, String densityFolder) {
        for (ResourceFolderType type : ResourceFolderType.values()) {
            if (type.getName().equals(densityFolder)) {
                return context.getProject().getResources(ResourceType.DRAWABLE).stream()
                        .filter(resource -> resource.getName().equals(iconName))
                        .filter(resource -> resource.getFolder().getName().equals(type.getName()))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private String getDensityString(int dpi) {
        switch (dpi) {
            case 120:
                return "ldpi";
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

    private float convertToDip(int pixels, int dpi) {
        return (pixels * 160f) / dpi;
    }

    private boolean isCloseEnough(float size1, float size2) {
        final float tolerance = 5.0f; // Tolerance in dip
        return Math.abs(size1 - size2) <= tolerance;
    }
}