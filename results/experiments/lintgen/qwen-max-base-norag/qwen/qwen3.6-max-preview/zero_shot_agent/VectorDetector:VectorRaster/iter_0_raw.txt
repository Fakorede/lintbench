package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class VectorDetector extends ResourceXmlDetector {

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
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_PATH_DATA = "pathData";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_VECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 24) {
            return;
        }

        checkChildren(context, element, minSdk);
    }

    private void checkChildren(XmlContext context, Element parent, int minSdk) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String tag = element.getTagName();

                if (TAG_GRADIENT.equals(tag)) {
                    if (minSdk < 24) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Gradient requires API 24 or higher for vector drawables. " +
                                "Raster generation may not support this feature correctly.");
                    }
                } else if (TAG_CLIP_PATH.equals(tag)) {
                    if (minSdk < 24) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "clip-path requires API 24 or higher for vector drawables. " +
                                "Raster generation may not support this feature correctly.");
                    }
                } else if (TAG_PATH.equals(tag)) {
                    String fillType = element.getAttributeNS(ANDROID_URI, ATTR_FILL_TYPE);
                    if (fillType != null && !fillType.isEmpty()) {
                        if (minSdk < 24) {
                            context.report(ISSUE, element, context.getLocation(element, ATTR_FILL_TYPE, ANDROID_URI),
                                    "fillType requires API 24 or higher for vector drawables. " +
                                    "Raster generation may not support this feature correctly.");
                        }
                    }

                    String pathData = element.getAttributeNS(ANDROID_URI, ATTR_PATH_DATA);
                    if (pathData != null && pathData.startsWith("@")) {
                        context.report(ISSUE, element, context.getLocation(element, ATTR_PATH_DATA, ANDROID_URI),
                                "pathData referencing a string resource is not supported by the raster image generator.");
                    }
                }

                checkChildren(context, element, minSdk);
            }
            child = child.getNextSibling();
        }
    }
}