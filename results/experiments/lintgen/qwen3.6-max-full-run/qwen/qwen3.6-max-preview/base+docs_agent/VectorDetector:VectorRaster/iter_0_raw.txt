package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "path", "group", "clip-path", "gradient");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        String tag = element.getTagName();

        if ("gradient".equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Gradient elements are not fully supported in vector rasterization for older devices");
        } else if ("clip-path".equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Clip-path elements are not fully supported in vector rasterization for older devices");
        } else if ("path".equals(tag) || "group".equals(tag)) {
            Attr fillType = element.getAttributeNode("android:fillType");
            if (fillType != null) {
                context.report(ISSUE, fillType, context.getLocation(fillType),
                        "fillType is not fully supported in vector rasterization for older devices");
            }

            Attr aaptAttr = element.getAttributeNode("aapt:attr");
            if (aaptAttr != null) {
                context.report(ISSUE, aaptAttr, context.getLocation(aaptAttr),
                        "aapt:attr is not fully supported in vector rasterization for older devices");
            }
        }
    }
}