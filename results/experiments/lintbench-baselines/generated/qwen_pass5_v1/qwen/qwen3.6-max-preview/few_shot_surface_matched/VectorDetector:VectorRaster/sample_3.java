package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the `drawable` folder is "
                            + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and "
                            + "bitmap images are generated for different screen resolutions for backwards "
                            + "compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, and "
                            + "this lint check flags elements and attributes that are not fully supported. "
                            + "You should manually check whether the generated output is acceptable for "
                            + "those older devices.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

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
        checkChildren(context, root);
    }

    private void checkChildren(@NonNull XmlContext context, @NonNull Node parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String tag = element.getTagName();

                if ("gradient".equals(tag)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Gradient elements are not fully supported in raster image generation for older API levels");
                } else if ("clip-path".equals(tag)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Clip-path elements are not fully supported in raster image generation for older API levels");
                } else if ("path".equals(tag)) {
                    String fillType = element.getAttribute("android:fillType");
                    if (fillType != null && !fillType.isEmpty()) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "The android:fillType attribute is not fully supported in raster image generation for older API levels");
                    }
                }
                checkChildren(context, child);
            }
        }
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }
}