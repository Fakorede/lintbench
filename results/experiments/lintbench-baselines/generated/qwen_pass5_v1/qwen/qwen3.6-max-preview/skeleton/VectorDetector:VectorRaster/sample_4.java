package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root != null && "vector".equals(root.getTagName())) {
            checkNode(context, root);
        }
    }

    private void checkNode(@NonNull XmlContext context, @NonNull Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element element = (Element) node;
        String tag = element.getTagName();

        if ("gradient".equals(tag)) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Gradient elements are not fully supported in vector drawables for backwards compatibility");
        }

        if (element.hasAttributeNS(ANDROID_URI, "fillType")) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, "fillType");
            context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "fillType attribute is not fully supported in vector drawables for backwards compatibility");
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