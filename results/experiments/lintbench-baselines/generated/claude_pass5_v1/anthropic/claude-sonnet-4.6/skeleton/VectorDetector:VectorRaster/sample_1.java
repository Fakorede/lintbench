package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_VECTOR;

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

    // Attributes that are not supported by the raster image generator
    private static final String[] UNSUPPORTED_ATTRIBUTES = {
        "autoMirrored",
        "fillType",
        "fillColor",
        "strokeColor",
    };

    // Tags that are not supported by the raster image generator
    private static final String[] UNSUPPORTED_TAGS = {
        "clip-path",
    };

    private static final String KEY_MIN_SDK = "minSdk";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Only check files in the drawable folder (not drawable-v21 etc.)
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String rootTag = root.getTagName();
        if (!TAG_VECTOR.equals(rootTag)) {
            return;
        }

        // Check if the project uses Gradle plugin 1.4+
        // We report incidents conditionally based on minSdkVersion
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check for unsupported tags (but skip the root <vector> tag itself for tag check)
        if (!TAG_VECTOR.equals(tagName)) {
            for (String unsupportedTag : UNSUPPORTED_TAGS) {
                if (unsupportedTag.equals(tagName)) {
                    String message =
                            "This tag (`"
                                    + tagName
                                    + "`) is not fully supported in the raster image generator; "
                                    + "check generated icon to make sure it looks acceptable";
                    Incident incident =
                            new Incident(ISSUE, element, context.getLocation(element), message);
                    LintMap map = new LintMap();
                    map.put(KEY_MIN_SDK, 21);
                    context.report(incident, map);
                    break;
                }
            }
        }

        // Check for unsupported attributes
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                if (!(attr instanceof Attr)) {
                    continue;
                }
                Attr attribute = (Attr) attr;
                String localName = attribute.getLocalName();
                if (localName == null) {
                    continue;
                }
                String ns = attribute.getNamespaceURI();
                if (!ANDROID_URI.equals(ns)) {
                    continue;
                }

                for (String unsupportedAttr : UNSUPPORTED_ATTRIBUTES) {
                    if (unsupportedAttr.equals(localName)) {
                        String message =
                                "This attribute (`"
                                        + localName
                                        + "`) is not fully supported in the raster image generator; "
                                        + "check generated icon to make sure it looks acceptable";
                        Incident incident =
                                new Incident(
                                        ISSUE, attribute, context.getLocation(attribute), message);
                        LintMap map = new LintMap();
                        map.put(KEY_MIN_SDK, 21);
                        context.report(incident, map);
                        break;
                    }
                }

                // Check for gradient fillColor (API 24+)
                if ("fillColor".equals(localName)) {
                    String value = attribute.getValue();
                    if (value != null && value.startsWith("@")) {
                        // Might be a gradient reference
                        String message =
                                "This attribute (`"
                                        + localName
                                        + "`) references a gradient which requires API 24; "
                                        + "check generated icon to make sure it looks acceptable";
                        Incident incident =
                                new Incident(
                                        ISSUE, attribute, context.getLocation(attribute), message);
                        LintMap map = new LintMap();
                        map.put(KEY_MIN_SDK, 24);
                        context.report(incident, map);
                    }
                }
            }
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                checkElement(context, (Element) child);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report the incident if the project's minSdkVersion is below the required API level
        // and the project uses a Gradle plugin that generates raster images
        Project project = context.getMainProject();
        if (!project.isGradleProject()) {
            return false;
        }

        int minSdk = project.getMinSdk();
        int requiredSdk = map.getInt(KEY_MIN_SDK, 21);

        // Only report if minSdkVersion is below the required level
        // (if minSdkVersion >= required, the vector is in the right folder already)
        return minSdk < requiredSdk;
    }
}