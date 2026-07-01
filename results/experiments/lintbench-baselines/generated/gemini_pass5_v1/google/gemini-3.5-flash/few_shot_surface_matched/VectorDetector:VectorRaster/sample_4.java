package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
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
                    new Implementation(
                            VectorDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("vector")) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, org.w3c.dom.Element element) {
        String tagName = element.getTagName();

        if ("clip-path".equals(tagName)) {
            report(context, element, "Clip paths are not supported by the rasterizer (API < 21)", 21);
        } else if ("gradient".equals(tagName)) {
            report(context, element, "Gradients are not supported by the rasterizer (API < 24)", 24);
        }

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attributes.item(i);
                String name = attr.getLocalName();
                String namespace = attr.getNamespaceURI();

                if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
                    if ("fillType".equals(name)) {
                        report(context, attr, "Fill type is not supported by the rasterizer (API < 24)", 24);
                    } else if ("autoMirrored".equals(name)) {
                        report(context, attr, "Auto-mirrored is not supported by the rasterizer (API < 21)", 21);
                    }
                }
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child);
            }
        }
    }

    private void report(XmlContext context, org.w3c.dom.Node node, String message, int requiredSdk) {
        Location location = context.getLocation(node);
        Incident incident = new Incident(ISSUE, location, message);
        incident.localTo(node);

        LintMap map = new LintMap();
        map.put("requiredSdk", requiredSdk);

        context.report(incident, map);
    }

    @Override
    public void filterIncident(
            Context context,
            Incident incident,
            LintMap map) {
        int minSdk = 1;
        if (context.getProject() != null) {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        }
        int requiredSdk = map.getInt("requiredSdk", 21);
        if (minSdk >= requiredSdk) {
            return;
        }
        super.filterIncident(context, incident, map);
    }
}