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
import org.w3c.dom.NodeList;

public class VectorDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GRADIENT = "gradient";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_STROKE_DASH_ARRAY = "strokeDashArray";
    private static final String ATTR_STROKE_DASH_OFFSET = "strokeDashOffset";
    private static final int API_RASTER_LIMIT = 24;

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector feature not supported by raster image generation",
            "When `minSdkVersion` is below the API level required by a vector drawable feature, "
                    + "the build system generates PNG bitmaps for backwards compatibility. "
                    + "Some features (gradients, clip-paths, fillType and dashed strokes) are not "
                    + "fully supported by this rasterization, so the generated images may look "
                    + "incorrect on older devices. Verify the generated output.",
            Category.CORRECTNESS,
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
        if (!isRasterizationRelevant(context, API_RASTER_LIMIT)) {
            return;
        }
        checkVectorContent(context, element);
    }

    private static void checkVectorContent(XmlContext context, Element root) {
        NodeList descendants = root.getElementsByTagName("*");
        for (int i = 0, n = descendants.getLength(); i < n; i++) {
            Element child = (Element) descendants.item(i);
            String tag = child.getTagName();
            if (TAG_GRADIENT.equals(tag)) {
                context.report(ISSUE, child, context.getLocation(child),
                        "Gradient fills/strokes are not supported when rasterizing vector "
                                + "drawables for older devices; verify the generated PNG output.");
            } else if (TAG_CLIP_PATH.equals(tag)) {
                context.report(ISSUE, child, context.getLocation(child),
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
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "fillType is not supported when rasterizing vector drawables for "
                                    + "older devices; verify the generated PNG output.");
                } else if (ATTR_STROKE_DASH_ARRAY.equals(name)
                        || ATTR_STROKE_DASH_OFFSET.equals(name)) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Dashed strokes are not supported when rasterizing vector drawables "
                                    + "for older devices; verify the generated PNG output.");
                }
            }
        }
    }

    private static boolean isRasterizationRelevant(XmlContext context, int requiredApi) {
        if (context.getMainProject().getMinSdk() >= requiredApi) {
            return false;
        }
        String folder = context.file.getParentFile().getName();
        return !folder.contains("-v" + requiredApi);
    }
}