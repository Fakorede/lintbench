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

    /**
     * Tags that are not supported by the vector rasterizer (API < 21).
     */
    private static final Set<String> UNSUPPORTED_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "clip-path",
                            "group"
                    ));

    /**
     * Attributes on the <vector> element that are not supported by the rasterizer.
     */
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            "autoMirrored",
                            "tint",
                            "tintMode"
                    ));

    /**
     * Attributes on <path> elements that are not supported by the rasterizer.
     */
    private static final Set<String> UNSUPPORTED_PATH_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            "trimPathStart",
                            "trimPathEnd",
                            "trimPathOffset",
                            "fillColor",
                            "fillAlpha",
                            "strokeColor",
                            "strokeAlpha",
                            "strokeLineCap",
                            "strokeLineJoin",
                            "strokeMiterLimit"
                    ));

    private static final String KEY_MIN_SDK = "minSdk";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Only flag issues in plain "drawable" folder (not drawable-v21, etc.)
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        // Check for unsupported attributes on the root <vector> element
        checkUnsupportedAttributes(context, root, UNSUPPORTED_VECTOR_ATTRS);

        // Walk the entire document tree looking for unsupported elements and attributes
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Check for unsupported child tags
        if (UNSUPPORTED_TAGS.contains(tagName)) {
            String message =
                    String.format(
                            "This tag (`%1$s`) is not supported in images generated from "
                                    + "this vector icon for API < 21; check generated icon",
                            tagName);
            Incident incident =
                    new Incident(ISSUE, element, context.getLocation(element), message);
            incident.setMap(new LintMap().put(KEY_MIN_SDK, 21));
            context.report(incident);
        }

        // Check path-specific unsupported attributes
        if ("path".equals(tagName)) {
            checkUnsupportedAttributes(context, element, UNSUPPORTED_PATH_ATTRS);
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

    private void checkUnsupportedAttributes(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Set<String> unsupportedAttrs) {
        NamedNodeMap attrs = element.getAttributes();
        if (attrs == null) {
            return;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
            }
            if (unsupportedAttrs.contains(localName) && ANDROID_URI.equals(attr.getNamespaceURI())) {
                String message =
                        String.format(
                                "The attribute `%1$s` is not supported in images generated "
                                        + "from this vector icon for API < 21; check generated icon",
                                localName);
                Incident incident =
                        new Incident(ISSUE, attr, context.getLocation(attr), message);
                incident.setMap(new LintMap().put(KEY_MIN_SDK, 21));
                context.report(incident);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only report the issue if minSdkVersion is below the required API level
        int minSdk = map.getInt(KEY_MIN_SDK, 21);
        Project project = context.getProject();
        int projectMinSdk = project.getMinSdk();
        // If minSdkVersion >= required API, no need to warn
        return projectMinSdk < minSdk;
    }
}