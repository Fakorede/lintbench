package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle "
                            + "plugin 1.4 or higher is used, a vector drawable placed in the "
                            + "`drawable` folder is automatically moved to `drawable-anydpi-v21` "
                            + "or `drawable-anydpi-v24` and bitmap images are generated for "
                            + "different screen resolutions for backwards compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, "
                            + "and this lint check flags elements and attributes that are not fully "
                            + "supported. You should manually check whether the generated output is "
                            + "acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("vector")) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("gradient".equals(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Gradients are not supported by the raster image generator");
        } else if ("clip-path".equals(tagName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Clip-paths are not supported by the raster image generator");
        }

        org.w3c.dom.Attr attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "fillType");
        if (attr == null) {
            attr = element.getAttributeNode("android:fillType");
        }
        if (attr != null) {
            context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "FillType is not supported by the raster image generator");
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    @Override
    public void filterIncident(Incident incident) {
        Project project = incident.getProject();
        if (project != null) {
            if (project.getMinSdkVersion() != null && project.getMinSdkVersion().getFeatureLevel() >= 21) {
                return;
            }
            Boolean useSupportLibrary = project.getVectorDrawablesUseSupportLibrary();
            if (Boolean.TRUE.equals(useSupportLibrary)) {
                return;
            }
        }
        super.filterIncident(incident);
    }
}