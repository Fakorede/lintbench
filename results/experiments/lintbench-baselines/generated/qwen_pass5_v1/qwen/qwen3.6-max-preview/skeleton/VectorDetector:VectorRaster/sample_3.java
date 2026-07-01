package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
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

    private static final String[] COLOR_ATTRIBUTES = {
            "android:fillColor", "android:strokeColor", "android:tint"
    };

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String tag = root.getTagName();
        if (!"vector".equals(tag) && !"animated-vector".equals(tag)) {
            return;
        }

        if (context.getMinSdk().getApiLevel() >= 24) {
            return;
        }

        checkNode(context, root);
    }

    private void checkNode(@NonNull XmlContext context, @NonNull Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element element = (Element) node;
        String tagName = element.getTagName();

        if ("gradient".equals(tagName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Gradients require API 24, and the generated raster images will not include them");
        }

        if (element.hasAttribute("android:fillType")) {
            Attr attr = element.getAttributeNode("android:fillType");
            context.report(ISSUE, attr, context.getLocation(attr),
                    "fillType requires API 24, and the generated raster images will not use it");
        }

        if (element.hasAttribute("android:theme")) {
            Attr attr = element.getAttributeNode("android:theme");
            context.report(ISSUE, attr, context.getLocation(attr),
                    "Theme attributes are not supported in vector drawables for raster generation");
        }

        for (String attrName : COLOR_ATTRIBUTES) {
            if (element.hasAttribute(attrName)) {
                Attr attr = element.getAttributeNode(attrName);
                String value = attr.getValue();
                if (value != null && value.startsWith("?")) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Theme references (" + value + ") are not supported in vector drawables for raster generation");
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            checkNode(context, children.item(i));
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }
}