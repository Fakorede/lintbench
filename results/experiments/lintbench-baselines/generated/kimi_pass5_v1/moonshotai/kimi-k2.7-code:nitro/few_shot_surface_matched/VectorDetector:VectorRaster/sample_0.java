package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is "
                    + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` "
                    + "and bitmap images are generated for different screen resolutions for "
                    + "backwards compatibility.\n"
                    + "\n"
                    + "However, there are some limitations to this raster image generation, and "
                    + "this check flags elements and attributes that are not fully supported. "
                    + "You should manually check whether the generated output is acceptable "
                    + "for those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String AAPT_ATTR = "attr";
    private static final String AAPT_NS = "http://schemas.android.com/aapt";

    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String VALUE_TRUE = "true";
    private static final String VALUE_EVEN_ODD = "evenOdd";

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(getLocalName(root))) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = getLocalName(element);
        boolean unsupported = false;

        if (TAG_CLIP_PATH.equals(tag)) {
            report(context, element,
                    "`<clip-path>` is not supported when generating raster images for older devices");
            unsupported = true;
        } else if (TAG_GRADIENT.equals(tag)) {
            report(context, element,
                    "Gradient fills are not supported when generating raster images for older devices");
            unsupported = true;
        } else if (AAPT_ATTR.equals(tag) && AAPT_NS.equals(element.getNamespaceURI())) {
            report(context, element,
                    "AAPT attribute wrappers are not supported when generating raster images for older devices");
            unsupported = true;
        }

        if (unsupported) {
            return;
        }

        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                org.w3c.dom.Node node = attributes.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ATTRIBUTE_NODE) {
                    checkAttribute(context, (org.w3c.dom.Attr) node);
                }
            }
        }

        org.w3c.dom.Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child);
            }
            child = child.getNextSibling();
        }
    }

    private void checkAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String name = getLocalName(attribute);
        String value = attribute.getValue();

        if (ATTR_FILL_TYPE.equals(name) && VALUE_EVEN_ODD.equals(value)) {
            report(context, attribute,
                    "`fillType=\"evenOdd\"` is not supported when generating raster images for older devices");
        } else if (ATTR_AUTO_MIRRORED.equals(name) && VALUE_TRUE.equals(value)) {
            report(context, attribute,
                    "`autoMirrored` is not supported when generating raster images for older devices");
        } else if (value != null && value.startsWith("?")) {
            report(context, attribute,
                    "Theme attribute references are not supported when generating raster images for older devices");
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        if (context.getProject().getMinSdkVersion() >= 21) {
            return false;
        }

        java.io.File file = context.file;
        if (file != null) {
            java.io.File parent = file.getParentFile();
            if (parent != null && isVersionedFolder(parent.getName(), 21)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isVersionedFolder(String folderName, int minApi) {
        int index = folderName.indexOf("-v");
        if (index == -1) {
            return false;
        }
        int start = index + 2;
        int end = start;
        while (end < folderName.length() && Character.isDigit(folderName.charAt(end))) {
            end++;
        }
        if (end == start) {
            return false;
        }
        try {
            int api = Integer.parseInt(folderName.substring(start, end));
            return api >= minApi;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String getLocalName(org.w3c.dom.Node node) {
        String localName = node.getLocalName();
        if (localName != null) {
            return localName;
        }
        String nodeName = node.getNodeName();
        int colon = nodeName.indexOf(':');
        return colon != -1 ? nodeName.substring(colon + 1) : nodeName;
    }

    private static void report(XmlContext context, org.w3c.dom.Node scope, String message) {
        context.report(ISSUE, scope, context.getLocation(scope), message);
    }
}