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

    // Tags not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "clip-path",
                            "group" // group with transformation is partially supported
                            ));

    // Attributes not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "autoMirrored",
                            "fillType",
                            "trimPathStart",
                            "trimPathEnd",
                            "trimPathOffset"));

    private static final String KEY_MIN_SDK = "minSdk";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // We handle everything in visitDocument
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Only applies to vector drawables
        Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        // Check if we're in a plain drawable folder (not drawable-v21 etc.)
        // The issue is only relevant if the file might be used for rasterization
        String folderName = context.file.getParentFile().getName();
        if (!folderName.equals("drawable")) {
            // If it's already in a versioned folder, no rasterization will happen
            // for older devices in the same way
            return;
        }

        // Walk the document tree and check for unsupported elements/attributes
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        // Check for unsupported tags (other than the root vector tag)
        if (UNSUPPORTED_TAGS.contains(tagName)) {
            String message =
                    String.format(
                            "This tag (`%1$s`) is not fully supported by the raster image generator;"
                                    + " check generated icon to verify appearance",
                            tagName);
            Incident incident =
                    new Incident(ISSUE, element, context.getLocation(element), message);
            incident.setMap(new LintMap().put(KEY_MIN_SDK, 21));
            context.report(incident);
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
                            String.format(
                                    "The attribute `%1$s` is not fully supported by the raster image generator;"
                                            + " check generated icon to verify appearance",
                                    attrName);
                    Incident incident =
                            new Incident(ISSUE, attr, context.getLocation(attr), message);
                    incident.setMap(new LintMap().put(KEY_MIN_SDK, 21));
                    context.report(incident);
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
        // Only report this issue if minSdkVersion < 21
        // (i.e., raster image generation would be triggered)
        int minSdk = context.getMainProject().getMinSdk();
        int requiredSdk = map.getInt(KEY_MIN_SDK, 21);
        return minSdk < requiredSdk;
    }
}