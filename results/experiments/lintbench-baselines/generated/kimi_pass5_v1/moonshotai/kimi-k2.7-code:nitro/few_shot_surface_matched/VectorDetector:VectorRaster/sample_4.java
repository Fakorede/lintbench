package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 "
                            + "or higher is used, a vector drawable placed in the `drawable` folder is "
                            + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` "
                            + "and bitmap images are generated for different screen resolutions for "
                            + "backwards compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image generation, "
                            + "and this lint check flags elements and attributes that are not fully "
                            + "supported. You should manually check whether the generated output is "
                            + "acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GROUP = "group";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";

    private static final String ATTR_TINT = "tint";
    private static final String ATTR_TINT_MODE = "tintMode";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";
    private static final String ATTR_STROKE_LINE_CAP = "strokeLineCap";
    private static final String ATTR_STROKE_LINE_JOIN = "strokeLineJoin";
    private static final String ATTR_STROKE_MITER_LIMIT = "strokeMiterLimit";
    private static final String ATTR_FILL_TYPE = "fillType";

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        checkVectorAttributes(context, root);
        checkChildren(context, root);
    }

    private static void checkVectorAttributes(XmlContext context, org.w3c.dom.Element element) {
        checkAttribute(context, element, ATTR_TINT);
        checkAttribute(context, element, ATTR_TINT_MODE);
        checkAttribute(context, element, ATTR_AUTO_MIRRORED);
    }

    private static void checkChildren(XmlContext context, org.w3c.dom.Element element) {
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }

            org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
            String tagName = childElement.getTagName();

            if (TAG_GROUP.equals(tagName)) {
                checkGroupAttributes(context, childElement);
                checkChildren(context, childElement);
            } else if (TAG_PATH.equals(tagName)) {
                checkPathAttributes(context, childElement);
                checkChildren(context, childElement);
            } else if (TAG_CLIP_PATH.equals(tagName)) {
                reportElement(
                        context,
                        childElement,
                        "The `<clip-path>` element is not supported when generating bitmaps for older devices");
            } else if (TAG_GRADIENT.equals(tagName)) {
                reportElement(
                        context,
                        childElement,
                        "The `<gradient>` element is not supported when generating bitmaps for older devices");
            } else {
                checkChildren(context, childElement);
            }
        }
    }

    private static void checkGroupAttributes(XmlContext context, org.w3c.dom.Element element) {
        checkAttribute(context, element, ATTR_NAME);
    }

    private static void checkPathAttributes(XmlContext context, org.w3c.dom.Element element) {
        checkAttribute(context, element, ATTR_TRIM_PATH_START);
        checkAttribute(context, element, ATTR_TRIM_PATH_END);
        checkAttribute(context, element, ATTR_TRIM_PATH_OFFSET);
        checkAttribute(context, element, ATTR_STROKE_LINE_CAP);
        checkAttribute(context, element, ATTR_STROKE_LINE_JOIN);
        checkAttribute(context, element, ATTR_STROKE_MITER_LIMIT);
        checkAttribute(context, element, ATTR_FILL_TYPE);
    }

    private static void checkAttribute(
            XmlContext context, org.w3c.dom.Element element, String attributeName) {
        if (element.hasAttributeNS(ANDROID_URI, attributeName)) {
            org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, attributeName);
            String message =
                    "The attribute `android:"
                            + attributeName
                            + "` is not supported when generating bitmaps for older devices";
            context.report(ISSUE, element, context.getLocation(attribute), message);
        }
    }

    private static void reportElement(
            XmlContext context, org.w3c.dom.Element element, String message) {
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        return context.getProject().getMinSdk() < 21;
    }
}