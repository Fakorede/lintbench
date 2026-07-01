package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.DOT_XML;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                    + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                    + "generated for different screen resolutions for backwards compatibility.\n\n"
                    + "However, there are some limitations to this raster image generation, and this "
                    + "lint check flags elements and attributes that are not fully supported. "
                    + "You should manually check whether the generated output is acceptable for those "
                    + "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_ANIMATED_VECTOR = "animated-vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return folderType == ResourceFolderType.DRAWABLE && fileName.endsWith(DOT_XML);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        String tag = root.getTagName();
        if (!TAG_VECTOR.equals(tag) && !TAG_ANIMATED_VECTOR.equals(tag)) {
            return;
        }
        traverse(context, root);
    }

    @Override
    protected boolean filterIncident(@NonNull XmlContext context, @NonNull Element element, @NonNull String attributeName) {
        return true;
    }

    private void traverse(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_CLIP_PATH.equals(tag)) {
            if (filterIncident(context, element, TAG_CLIP_PATH)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Clip paths are not fully supported in raster image generation");
            }
        }

        checkAttr(context, element, ATTR_FILL_TYPE);
        checkAttr(context, element, ATTR_TRIM_PATH_START);
        checkAttr(context, element, ATTR_TRIM_PATH_END);
        checkAttr(context, element, ATTR_TRIM_PATH_OFFSET);

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                traverse(context, (Element) child);
            }
        }
    }

    private void checkAttr(@NonNull XmlContext context, @NonNull Element element, @NonNull String attrName) {
        if (element.hasAttributeNS(ANDROID_URI, attrName)) {
            if (filterIncident(context, element, attrName)) {
                context.report(ISSUE, element,
                        context.getLocation(element.getAttributeNodeNS(ANDROID_URI, attrName)),
                        "`" + attrName + "` is not fully supported in raster image generation");
            }
        }
    }
}