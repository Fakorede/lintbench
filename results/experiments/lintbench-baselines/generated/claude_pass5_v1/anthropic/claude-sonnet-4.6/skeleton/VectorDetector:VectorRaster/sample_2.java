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
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                            + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                            + "generated for different screen resolutions for backwards compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image generation, and this "
                            + "lint check flags elements and attributes that are not fully supported. "
                            + "You should manually check whether the generated output is acceptable for those "
                            + "older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Attributes and elements not supported by the vector rasterizer
    // These are features that require API 24 (gradient, etc.) or are simply not supported
    private static final String[] UNSUPPORTED_ELEMENTS = {
        "aapt:attr",
        "gradient",
    };

    private static final String[] UNSUPPORTED_ATTRIBUTES = {
        "android:fillType",
        "android:strokeMiterLimit",
        "android:strokeLineCap",
        "android:strokeLineJoin",
    };

    // Android namespace
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final String KEY_MIN_SDK = "minSdk";

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

        // Only applies to vector drawables
        if (!"vector".equals(root.getTagName()) && !"vector".equals(root.getLocalName())) {
            return;
        }

        // Check if this is in a plain "drawable" folder (not drawable-v21 etc.)
        // The raster generation only applies when the vector is in the base drawable folder
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            return;
        }

        // Check for unsupported elements and attributes throughout the document
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check if this element itself is unsupported
        for (String unsupported : UNSUPPORTED_ELEMENTS) {
            // Handle namespace prefix in element names
            String localUnsupported = unsupported;
            if (unsupported.contains(":")) {
                localUnsupported = unsupported.substring(unsupported.indexOf(':') + 1);
            }
            if (tagName.equals(localUnsupported) || element.getTagName().equals(unsupported)) {
                String message =
                        "This element or attribute is not supported for older versions by "
                                + "the image generation; some devices may show incorrect images";
                Incident incident =
                        new Incident(ISSUE, element, context.getLocation(element), message);
                incident.getClientProperties().put(KEY_MIN_SDK, 1);
                context.report(incident, map().put(KEY_MIN_SDK, 1));
                // Don't return - continue checking children
            }
        }

        // Check attributes on this element
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                String attrName = attr.getName();
                for (String unsupported : UNSUPPORTED_ATTRIBUTES) {
                    if (attrName.equals(unsupported)) {
                        String message =
                                "This element or attribute is not supported for older versions by "
                                        + "the image generation; some devices may show incorrect images";
                        Incident incident =
                                new Incident(ISSUE, attr, context.getLocation(attr), message);
                        context.report(incident, map().put(KEY_MIN_SDK, 1));
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
                checkElement(context, (Element) child);
            }
        }
    }

    private LintMap map() {
        return new LintMap();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report if minSdkVersion < 21 (i.e., the raster generation is actually in play)
        // If minSdk >= 21, no raster generation happens and the issue doesn't apply
        Project project = context.getMainProject();
        int minSdk = project.getMinSdk();
        // Report the issue only when minSdkVersion < 21, meaning raster generation is used
        return minSdk < 21;
    }
}