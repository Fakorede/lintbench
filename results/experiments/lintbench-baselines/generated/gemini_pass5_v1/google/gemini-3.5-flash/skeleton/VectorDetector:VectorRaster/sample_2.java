package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class VectorDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

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
                            + "and this lint check flags elements and attributes that are not "
                            + "fully supported. You should manually check whether the generated "
                            + "output is acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

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
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("clip-path".equals(tagName)) {
            report(context, element, context.getNameLocation(element),
                    "The `<clip-path>` element is not supported by the SVG-to-PNG generator. "
                            + "Your rasterized image might look different.", 21);
        } else if ("gradient".equals(tagName)) {
            report(context, element, context.getNameLocation(element),
                    "Gradients are not supported by the SVG-to-PNG generator. "
                            + "Your rasterized image might look different.", 24);
        }

        // Check attributes
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            String value = attr.getValue();

            if ("autoMirrored".equals(name) && "vector".equals(tagName)) {
                report(context, attr, context.getValueLocation(attr),
                        "The `autoMirrored` attribute is not supported by the SVG-to-PNG generator. "
                                + "Your rasterized image might look different.", 21);
            }

            // Check for theme references in attributes (e.g., ?attr/foo)
            if (value.startsWith("?")) {
                report(context, attr, context.getValueLocation(attr),
                        "Theme references (such as `" + value + "`) are not supported by the SVG-to-PNG generator. "
                                + "Your rasterized image might look different.", 21);
            }
        }

        // Recursively check children
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
            child = child.getNextSibling();
        }
    }

    private void report(@NonNull XmlContext context, @NonNull Node node,
                        @NonNull Location location, @NonNull String message, int requiredSdk) {
        Incident incident = new Incident(ISSUE, node, location, message);
        LintMap map = LintMap.create();
        map.put("requiredSdk", requiredSdk);
        context.report(incident, map);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredSdk = map.getInt("requiredSdk", 21);
        if (context.getProject().getMinSdk() >= requiredSdk) {
            return false;
        }
        if (isUsingSupportLibrary(context)) {
            return false;
        }
        return true;
    }

    private boolean isUsingSupportLibrary(Context context) {
        Project project = context.getProject();
        for (String dep : project.getDependsOn()) {
            if (dep.contains("support-vector-drawable") || dep.contains("vectordrawable")) {
                return true;
            }
        }
        return false;
    }
}