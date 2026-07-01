package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Attr;
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
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("gradient".equals(tagName)) {
            Incident incident = new Incident(ISSUE, element, context.getNameLocation(element),
                    "Gradients are only supported in vector drawables on API 24 and above");
            LintMap map = new LintMap();
            map.put("minSdk", 24);
            context.report(incident, map);
        }

        Attr fillTypeAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "fillType");
        if (fillTypeAttr != null) {
            Incident incident = new Incident(ISSUE, fillTypeAttr, context.getLocation(fillTypeAttr),
                    "`fillType` is only supported in vector drawables on API 24 and above");
            LintMap map = new LintMap();
            map.put("minSdk", 24);
            context.report(incident, map);
        }

        Attr autoMirroredAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "autoMirrored");
        if (autoMirroredAttr != null) {
            Incident incident = new Incident(ISSUE, autoMirroredAttr, context.getLocation(autoMirroredAttr),
                    "`autoMirrored` is only supported in vector drawables on API 21 and above");
            LintMap map = new LintMap();
            map.put("minSdk", 21);
            context.report(incident, map);
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
    public boolean filterIncident(
            @NonNull Incident incident,
            @NonNull Context context,
            @NonNull LintMap map) {
        int requiredSdk = map.getInt("minSdk", 21);
        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        return minSdk < requiredSdk;
    }
}