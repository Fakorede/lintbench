package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GRADIENT = "gradient";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_STROKE_DASH_ARRAY = "strokeDashArray";
    private static final String ATTR_STROKE_DASH_OFFSET = "strokeDashOffset";
    private static final int MAX_DIMENSION_DP = 200;
    private static final int VECTOR_API = 21;
    private static final int GRADIENTS_API = 24;

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector image generation",
            "When a vector drawable is used on devices running API 20 or lower, the Android Gradle "
                    + "plugin generates PNG bitmaps for backwards compatibility. Some vector "
                    + "features are not supported by this rasterization, including gradients, "
                    + "clip-paths, fillType and dashed strokes. In addition, very large vector "
                    + "icons can produce large bitmaps and memory issues. You should manually "
                    + "verify the generated output is acceptable for older devices.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_VECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_VECTOR.equals(element.getTagName())) {
            return;
        }

        checkDimensions(context, element);

        NodeList descendants = element.getElementsByTagName("*");
        for (int i = 0, n = descendants.getLength(); i < n; i++) {
            Element child = (Element) descendants.item(i);
            String tag = child.getTagName();
            if (TAG_GRADIENT.equals(tag)) {
                reportIfRelevant(context, child, GRADIENTS_API,
                        "Gradient fills/strokes are not supported when rasterizing vector "
                                + "drawables for older devices; verify the generated PNG output.");
            } else if (TAG_CLIP_PATH.equals(tag)) {
                reportIfRelevant(context, child, VECTOR_API,
                        "Clip paths are not supported when rasterizing vector drawables for "
                                + "older devices; verify the generated PNG output.");
            }

            NamedNodeMap attributes = child.getAttributes();
            for (int j = 0, m = attributes.getLength(); j < m; j++) {
                Attr attr = (Attr) attributes.item(j);
                String name = attr.getLocalName();
                if (name == null) {
                    continue;
                }
                if (ATTR_FILL_TYPE.equals(name)) {
                    reportIfRelevant(context, attr, GRADIENTS_API,
                            "fillType is not supported when rasterizing vector drawables for "
                                    + "older devices; verify the generated PNG output.");
                } else if (ATTR_STROKE_DASH_ARRAY.equals(name)
                        || ATTR_STROKE_DASH_OFFSET.equals(name)) {
                    reportIfRelevant(context, attr, VECTOR_API,
                            "Dashed strokes are not supported when rasterizing vector drawables "
                                    + "for older devices; verify the generated PNG output.");
                }
            }
        }
    }

    private static void checkDimensions(XmlContext context, Element vector) {
        if (!isRasterizationRelevant(context, VECTOR_API)) {
            return;
        }
        int width = getDimension(vector, "width");
        int height = getDimension(vector, "height");
        if (width > MAX_DIMENSION_DP || height > MAX_DIMENSION_DP) {
            context.report(ISSUE, vector, context.getLocation(vector),
                    String.format("This vector drawable is very large (%1$dx%2$d dp) and may lead "
                            + "to a large generated bitmap and memory issues when rasterized for "
                            + "older devices. Consider reducing the width/height or using a "
                            + "version-qualified drawable folder.", width, height));
        }
    }

    private static void reportIfRelevant(XmlContext context, Node node, int api, String message) {
        if (isRasterizationRelevant(context, api)) {
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }

    private static boolean isRasterizationRelevant(XmlContext context, int api) {
        if (context.getMainProject().getMinSdk() >= api) {
            return false;
        }
        String folder = context.file.getParentFile().getName();
        return !folder.endsWith("-v" + api);
    }

    private static int getDimension(Element element, String name) {
        String value = element.getAttributeNS(ANDROID_URI, name);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        value = value.trim();
        int end = 0;
        while (end < value.length()
                && (Character.isDigit(value.charAt(end)) || value.charAt(end) == '.')) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        String number = value.substring(0, end);
        String unit = value.substring(end).trim();
        if (!unit.isEmpty() && !"dp".equals(unit)) {
            return 0;
        }
        try {
            return (int) Float.parseFloat(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}