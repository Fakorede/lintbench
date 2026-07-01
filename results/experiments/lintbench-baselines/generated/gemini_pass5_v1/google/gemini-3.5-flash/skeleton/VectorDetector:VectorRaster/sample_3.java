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
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;

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
                            + "and this lint check flags elements and attributes that are not fully "
                            + "supported. You should manually check whether the generated output is "
                            + "acceptable for those older devices.",
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
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("vector")) {
            return;
        }

        Boolean useSupportLib = context.getProject().getVectorDrawablesUseSupportLibrary();
        if (useSupportLib == Boolean.TRUE) {
            return;
        }

        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 21) {
            return;
        }

        int minSdkVersion = 1;
        if (context.getProject().getMinSdkVersion() != null) {
            minSdkVersion = context.getProject().getMinSdkVersion().getFeatureLevel();
        }

        if (minSdkVersion >= 24) {
            return;
        }

        checkNode(context, root, minSdkVersion);
    }

    private void checkNode(@NonNull XmlContext context, @NonNull org.w3c.dom.Node node, int minSdkVersion) {
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            String tagName = element.getTagName();

            if (tagName.equals("clip-path")) {
                if (minSdkVersion < 21) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "The `<clip-path>` element is not supported by the PNG generator");
                }
            } else if (tagName.equals("gradient") || tagName.endsWith("Gradient")) {
                if (minSdkVersion < 24) {
                    context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Gradients are not supported by the PNG generator");
                }
            }

            org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    org.w3c.dom.Node attr = attributes.item(i);
                    String value = attr.getNodeValue();
                    String name = attr.getNodeName();

                    if (name.endsWith(":fillType") && value.equalsIgnoreCase("evenOdd")) {
                        if (minSdkVersion < 24) {
                            context.report(
                                    ISSUE,
                                    attr,
                                    context.getLocation(attr),
                                    "The `evenOdd` fillType is not supported by the PNG generator");
                        }
                    }

                    if (value.startsWith("?")) {
                        if (minSdkVersion < 21) {
                            context.report(
                                    ISSUE,
                                    attr,
                                    context.getLocation(attr),
                                    "Theme references (such as `" + value + "`) are not supported by the PNG generator");
                        }
                    }
                }
            }
        }

        org.w3c.dom.NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child != null) {
                    checkNode(context, child, minSdkVersion);
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }
}