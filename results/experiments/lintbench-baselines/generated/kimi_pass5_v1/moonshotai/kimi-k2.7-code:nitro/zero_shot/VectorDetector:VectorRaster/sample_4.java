package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, "
                    + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                    + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                    + "generated for different screen resolutions for backwards compatibility.\n"
                    + "\n"
                    + "However, there are some limitations to this raster image generation, and this "
                    + "lint check flags elements and attributes that are not fully supported. You should "
                    + "manually check whether the generated output is acceptable for those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";

    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";
    private static final String ATTR_STROKE_LINE_CAP = "strokeLineCap";
    private static final String ATTR_STROKE_LINE_JOIN = "strokeLineJoin";
    private static final String ATTR_STROKE_MITER_LIMIT = "strokeMiterLimit";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_TINT_MODE = "tintMode";

    private static final int VECTOR_DRAWABLE_RASTER_API = 24;

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_VECTOR);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (TAG_VECTOR.equals(element.getTagName())) {
            checkElement(context, element);
        }
    }

    private void checkElement(@NotNull XmlContext context, @NotNull Element element) {
        String tagName = element.getTagName();

        if (TAG_CLIP_PATH.equals(tagName)) {
            reportIfRasterized(context, element, VECTOR_DRAWABLE_RASTER_API,
                    "Raster image generation does not support `<clip-path>` elements");
        } else if (TAG_GRADIENT.equals(tagName)) {
            reportIfRasterized(context, element, VECTOR_DRAWABLE_RASTER_API,
                    "Raster image generation does not support gradients");
        } else if (TAG_PATH.equals(tagName)) {
            checkAttribute(context, element, ATTR_FILL_TYPE, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_TRIM_PATH_START, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_TRIM_PATH_END, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_TRIM_PATH_OFFSET, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_STROKE_LINE_CAP, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_STROKE_LINE_JOIN, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_STROKE_MITER_LIMIT, VECTOR_DRAWABLE_RASTER_API);
        } else if (TAG_VECTOR.equals(tagName)) {
            checkAttribute(context, element, ATTR_AUTO_MIRRORED, VECTOR_DRAWABLE_RASTER_API);
            checkAttribute(context, element, ATTR_TINT_MODE, VECTOR_DRAWABLE_RASTER_API);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    private void checkAttribute(@NotNull XmlContext context, @NotNull Element element,
            @NotNull String attributeName, int requiredApi) {
        if (element.hasAttributeNS(ANDROID_URI, attributeName)) {
            Attr attribute = element.getAttributeNodeNS(ANDROID_URI, attributeName);
            String message = String.format(
                    "Raster image generation does not support the `android:%1$s` attribute",
                    attributeName);
            reportIfRasterized(context, attribute, requiredApi, message);
        }
    }

    private void reportIfRasterized(@NotNull XmlContext context, @NotNull Node node,
            int requiredApi, @NotNull String message) {
        int folderVersion = context.getFolderVersion();
        if (folderVersion >= requiredApi) {
            return;
        }
        if (context.getMainProject().getMinSdk() >= requiredApi) {
            return;
        }
        context.report(ISSUE, node, context.getLocation(node), message);
    }
}