package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
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
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
                            + "different screen resolutions for backwards compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image "
                            + "generation, and this lint check flags elements and attributes "
                            + "that are not fully supported. You should manually check whether "
                            + "the generated output is acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    // Elements not supported by the vector rasterizer
    private static final String[] UNSUPPORTED_ELEMENTS = {
        "clip-path",
    };

    // Attributes not supported by the vector rasterizer
    private static final String[] UNSUPPORTED_ATTRIBUTES = {
        "android:fillType",
        "android:strokeLineCap",
        "android:strokeLineJoin",
        "android:strokeMiterLimit",
        "android:trimPathEnd",
        "android:trimPathOffset",
        "android:trimPathStart",
    };

    // Gradient-related elements not supported by rasterizer
    private static final String GRADIENT_ELEMENT = "gradient";
    private static final String AAPT_ATTR_ELEMENT = "aapt:attr";

    public VectorDetector() {}

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

        // Only process vector drawables
        if (!"vector".equals(root.getTagName())) {
            return;
        }

        // Collect all issues found in this document
        List<String> problems = new ArrayList<>();
        checkElement(root, problems, context);

        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(
                    "This vector drawable includes elements or attributes not supported "
                            + "by the image generation fallback for older API versions: ");
            message.append(String.join(", ", problems));
            message.append(". You should check the generated image to make sure it looks "
                    + "correct.");

            Incident incident =
                    new Incident(
                            ISSUE,
                            root,
                            context.getLocation(root),
                            message.toString());
            context.report(incident, map());
        }
    }

    private void checkElement(
            @NonNull Element element,
            @NonNull List<String> problems,
            @NonNull XmlContext context) {

        String tagName = element.getTagName();

        // Check for unsupported elements
        for (String unsupported : UNSUPPORTED_ELEMENTS) {
            if (unsupported.equals(tagName)) {
                if (!problems.contains(tagName)) {
                    problems.add("`<" + tagName + ">`");
                }
                break;
            }
        }

        // Check for gradient element
        if (GRADIENT_ELEMENT.equals(tagName)) {
            if (!problems.contains(GRADIENT_ELEMENT)) {
                problems.add("`<" + GRADIENT_ELEMENT + ">`");
            }
        }

        // Check for aapt:attr element (used for gradients inline)
        if (AAPT_ATTR_ELEMENT.equals(tagName)) {
            if (!problems.contains(AAPT_ATTR_ELEMENT)) {
                problems.add("`<" + AAPT_ATTR_ELEMENT + ">`");
            }
        }

        // Check for unsupported attributes
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String attrName = attr.getNodeName();
                for (String unsupported : UNSUPPORTED_ATTRIBUTES) {
                    // Match by local name with namespace prefix
                    if (unsupported.equals(attrName)
                            || unsupported.endsWith(":" + attr.getLocalName())) {
                        if (!problems.contains(attrName)) {
                            problems.add("`" + attrName + "`");
                        }
                        break;
                    }
                }
            }
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement((Element) child, problems, context);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Only report if minSdkVersion < 21
        if (context.getMainProject().getMinSdk() >= 21) {
            return false;
        }
        return true;
    }

    @Nullable
    private static LintMap map() {
        return new LintMap();
    }
}