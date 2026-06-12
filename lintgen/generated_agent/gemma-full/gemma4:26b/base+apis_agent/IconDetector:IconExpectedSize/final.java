package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements XmlScanner {

    private static final Map<String, Integer> EXPECTED_SIZES = new HashMap<>();
    static {
        EXPECTED_SIZES.put("mdpi", 48);
        EXPECTED_SIZES.put("hdpi", 72);
        EXPECTED_SIZES.put("xhdpi", 96);
        EXPECTED_SIZES.put("xxhdpi", 144);
        EXPECTED_SIZES.put("xxxhdpi", 192);
    }

    public static final Issue ISSUE = Issue.create(
            "IconIncorrectSize",
            "Launcher icon has incorrect size",
            "Launcher icons in mipmap folders should follow the predefined sizes for each density.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new com.android.tools.lint.detector.api.Implementation(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("adaptive-icon", "bitmap");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String uri = element.getOwnerDocument().getBaseURI();
        String density = getDensityFromUri(uri);

        if (density != null && EXPECTED_SIZES.containsKey(density)) {
            int expectedSize = EXPECTSE_SIZES.get(density);
            checkAttribute(context, element, "android:width", expectedSize, density);
            checkAttribute(context, element, "width", expectedSize, density);
            checkAttribute(context, element, "android:height", expectedSize, density);
            checkAttribute(context, element, "height", expectedSize, density);
        }
    }

    private String getDensityFromUri(String uri) {
        if (uri == null) return null;
        if (uri.contains("-mdpi")) return "mdpi";
        if (uri.contains("-hdpi")) return "hdpi";
        if (uri.contains("-xhdpi")) return "xhdpi";
        if (uri.contains("-xxhdpi")) return "xxhdpi";
        if (uri.contains("-xxxhdpi")) return "xxxhdpi";
        return null;
    }

    private void checkAttribute(XmlContext context, Element element, String attrName, int expected, String density) {
        if (element.hasAttribute(attrName)) {
            String valueStr = element.getAttribute(attrName);
            try {
                String numericValue = valueStr.replaceAll("[^0-9]", "");
                if (!numericValue.isEmpty()) {
                    int value = Integer.parseInt(numericValue);
                    if (value != expected) {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                String.format("Launcher icon size should be %d for %s density.", expected, density)
                        );
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore non-numeric attributes
            }
        }
    }

    private static final Map<String, Integer> EXPECTSE_SIZES = EXPECTED_SIZES;
}