package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, " +
                    "but when minSdkVersion is less than 21 or 24 and Android Gradle plugin 1.4 or " +
                    "higher is used, a vector drawable placed in the drawable folder is automatically " +
                    "moved to drawable-anydpi-v21 or drawable-anydpi-v24 and bitmap images are " +
                    "generated for different screen resolutions for backwards compatibility.\n\n" +
                    "However, there are some limitations to this raster image generation, and this " +
                    "lint check flags elements and attributes that are not fully supported. " +
                    "You should manually check whether the generated output is acceptable for those " +
                    "older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }
        visitElement(context, root);
    }

    private void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        checkElement(context, element);
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                visitElement(context, (Element) child);
            }
        }
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if ("gradient".equals(tag)) {
            context.report(ISSUE, context.getLocation(element),
                    "Gradient elements require API 24 or higher and are not fully supported during rasterization for older devices.");
            return;
        }

        Attr fillType = element.getAttributeNodeNS(ANDROID_URI, "fillType");
        if (fillType != null) {
            context.report(ISSUE, context.getLocation(fillType),
                    "fillType requires API 24 or higher and is not fully supported during rasterization for older devices.");
        }

        Attr trimPathStart = element.getAttributeNodeNS(ANDROID_URI, "trimPathStart");
        if (trimPathStart != null) {
            context.report(ISSUE, context.getLocation(trimPathStart),
                    "trimPathStart is not fully supported during rasterization for older devices.");
        }

        Attr trimPathEnd = element.getAttributeNodeNS(ANDROID_URI, "trimPathEnd");
        if (trimPathEnd != null) {
            context.report(ISSUE, context.getLocation(trimPathEnd),
                    "trimPathEnd is not fully supported during rasterization for older devices.");
        }

        Attr trimPathOffset = element.getAttributeNodeNS(ANDROID_URI, "trimPathOffset");
        if (trimPathOffset != null) {
            context.report(ISSUE, context.getLocation(trimPathOffset),
                    "trimPathOffset is not fully supported during rasterization for older devices.");
        }

        Attr pathData = element.getAttributeNodeNS(ANDROID_URI, "pathData");
        if (pathData != null) {
            String value = pathData.getValue();
            if (value != null && (value.indexOf('A') != -1 || value.indexOf('a') != -1)) {
                context.report(ISSUE, context.getLocation(pathData),
                        "Arc commands (A/a) in pathData are not fully supported during rasterization for older devices.");
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            int version = xmlContext.getFolderVersion();
            if (version >= 21) {
                return false;
            }
        }
        return true;
    }
}