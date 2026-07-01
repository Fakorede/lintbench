package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class VectorDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, " +
            "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
            "higher is used, a vector drawable placed in the `drawable` folder is automatically " +
            "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n\n" +
            "However, there are some limitations to this raster image generation, and this " +
            "lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for those " +
            "older devices.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int MAX_SIZE = 200;
    private static final String AAPT_URI = "http://schemas.android.com/aapt";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "path", "gradient", "group", "clip-path");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if ("vector".equals(tag)) {
            checkSize(context, element, SdkConstants.ATTR_WIDTH);
            checkSize(context, element, SdkConstants.ATTR_HEIGHT);
        }

        if ("gradient".equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Gradient elements are not fully supported in vector rasterization for older devices");
            return;
        }

        Attr fillType = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "fillType");
        if (fillType != null) {
            context.report(ISSUE, fillType, context.getLocation(fillType),
                    "fillType is not fully supported in vector rasterization for older devices");
        }

        Attr aaptAttr = element.getAttributeNodeNS(AAPT_URI, "attr");
        if (aaptAttr != null) {
            context.report(ISSUE, aaptAttr, context.getLocation(aaptAttr),
                    "aapt:attr is not fully supported in vector rasterization for older devices");
        }

        Attr trimStart = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "trimPathStart");
        if (trimStart != null) {
            context.report(ISSUE, trimStart, context.getLocation(trimStart),
                    "trimPathStart is not fully supported in vector rasterization for older devices");
        }

        Attr trimEnd = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "trimPathEnd");
        if (trimEnd != null) {
            context.report(ISSUE, trimEnd, context.getLocation(trimEnd),
                    "trimPathEnd is not fully supported in vector rasterization for older devices");
        }

        Attr trimOffset = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "trimPathOffset");
        if (trimOffset != null) {
            context.report(ISSUE, trimOffset, context.getLocation(trimOffset),
                    "trimPathOffset is not fully supported in vector rasterization for older devices");
        }
    }

    private void checkSize(XmlContext context, Element element, String attrName) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attrName);
        if (attr != null) {
            String value = attr.getValue().trim();
            if (value.endsWith("dp") || value.endsWith("dip")) {
                int suffixLen = value.endsWith("dp") ? 2 : 3;
                try {
                    float size = Float.parseFloat(value.substring(0, value.length() - suffixLen).trim());
                    if (size > MAX_SIZE) {
                        context.report(ISSUE, attr, context.getLocation(attr),
                                "Vector icons should be at most " + MAX_SIZE + "x" + MAX_SIZE + " dp; this icon is larger");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }
}