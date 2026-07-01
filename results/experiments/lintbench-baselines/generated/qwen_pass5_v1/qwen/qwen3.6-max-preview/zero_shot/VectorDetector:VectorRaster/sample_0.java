package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class VectorDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GRADIENT = "gradient";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_PATH = "path";
    private static final String ATTR_FILL_TYPE = "fillType";

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
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_GRADIENT, TAG_CLIP_PATH, TAG_PATH);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        String tag = element.getTagName();
        switch (tag) {
            case TAG_GRADIENT:
                context.report(ISSUE, element, context.getLocation(element),
                        "Gradients in vector drawables are not fully supported during raster image generation (requires API 24)");
                break;
            case TAG_CLIP_PATH:
                context.report(ISSUE, element, context.getLocation(element),
                        "Clip paths in vector drawables are not fully supported during raster image generation");
                break;
            case TAG_PATH:
                Attr fillType = element.getAttributeNodeNS(ANDROID_URI, ATTR_FILL_TYPE);
                if (fillType != null) {
                    context.report(ISSUE, fillType, context.getLocation(fillType),
                            "fillType in vector drawables is not fully supported during raster image generation (requires API 24)");
                }
                break;
        }
    }
}