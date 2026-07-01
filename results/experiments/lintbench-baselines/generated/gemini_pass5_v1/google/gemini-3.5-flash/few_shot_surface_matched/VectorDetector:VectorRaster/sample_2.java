package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

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
                            VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        if (!"vector".equals(root.getTagName())) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("clip-path".equals(tagName)) {
            report(context, element, "The clipping shape, `<clip-path>`, is not supported by the generator and will be ignored in the generated bitmaps", false);
        } else if ("gradient".equals(tagName)) {
            report(context, element, "Gradients are only supported in API 24 and higher; the PNG generator will not be able to render them", true);
        }

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attributes.item(i);
                String name = attr.getLocalName();
                if (name == null) {
                    name = attr.getName();
                }
                String value = attr.getValue();
                if ("autoMirrored".equals(name) && "true".equals(value)) {
                    report(context, attr, "The `autoMirrored` attribute is not supported by the generator and will be ignored in the generated bitmaps", false);
                } else if (value != null && value.startsWith("?")) {
                    if ("fillColor".equals(name) || "strokeColor".equals(name) || "tint".equals(name)) {
                        report(context, attr, "Theme references are not supported by the generator and will be ignored in the generated bitmaps", false);
                    }
                }
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    checkElement(context, (org.w3c.dom.Element) child);
                }
            }
        }
    }

    private void report(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Node node,
            @com.android.annotations.NonNull String message,
            boolean isGradient) {
        Incident incident = new Incident(ISSUE, context.getLocation(node), message);
        LintMap map = LintMap.create();
        map.put("is_gradient", isGradient);
        context.report(incident, map);
    }

    @Override
    public void filterIncident(
            @com.android.annotations.NonNull Context context,
            @com.android.annotations.NonNull Incident incident,
            @com.android.annotations.NonNull LintMap map) {
        Boolean useSupportLibrary = context.getProject().getVectorDrawablesUseSupportLibrary();
        if (Boolean.TRUE.equals(useSupportLibrary)) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        boolean isGradient = map.getBoolean("is_gradient", false);
        if (isGradient) {
            if (minSdk >= 24) {
                return;
            }
        } else {
            if (minSdk >= 21) {
                return;
            }
        }

        context.report(incident);
    }
}