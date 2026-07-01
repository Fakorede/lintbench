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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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

    // Tags not supported in the raster image generator
    private static final Set<String> UNSUPPORTED_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "clip-path",
                            "group" // group with transformation not fully supported
                            ));

    // Attributes not supported in the raster image generator
    private static final Set<String> UNSUPPORTED_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "fillType",
                            "trimPathStart",
                            "trimPathEnd",
                            "trimPathOffset",
                            "strokeLineCap",
                            "strokeLineJoin",
                            "strokeMiterLimit",
                            "fillColor" // when using gradient fills
                            ));

    private static final String KEY_MIN_SDK = "minSdk";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
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

        // Only process vector drawables
        if (!TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check for unsupported tags (except the root vector tag and path tag)
        if (!"vector".equals(tagName) && !"path".equals(tagName)) {
            if (UNSUPPORTED_TAGS.contains(tagName)) {
                String message =
                        "This tag is not supported in images generated from this vector icon for "
                                + "API < 21; check generated icon to make sure it looks acceptable";
                Incident incident = new Incident(ISSUE, element, context.getLocation(element), message);
                LintMap map = new LintMap();
                map.put(KEY_MIN_SDK, 21);
                context.report(incident, map);
            }
        }

        // Check for unsupported attributes
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                String attrName = attr.getLocalName();
                if (attrName == null) {
                    attrName = attr.getName();
                }
                if (UNSUPPORTED_ATTRIBUTES.contains(attrName)) {
                    String message =
                            "The attribute `"
                                    + attrName
                                    + "` is not supported in images generated from this vector icon "
                                    + "for API < 21; check generated icon to make sure it looks acceptable";
                    Incident incident = new Incident(ISSUE, attr, context.getLocation(attr), message);
                    LintMap map = new LintMap();
                    map.put(KEY_MIN_SDK, 21);
                    context.report(incident, map);
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

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report if minSdkVersion is below the required API level
        int minSdk = map.getInt(KEY_MIN_SDK, 1);
        int projectMinSdk = context.getMainProject().getMinSdk();
        // Return true to accept/report the incident, false to filter it out
        return projectMinSdk < minSdk;
    }
}