package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
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
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "minSdkVersion is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the drawable folder is "
                            + "automatically moved to drawable-anydpi-v21 or drawable-anydpi-v24 and "
                            + "bitmap images are generated for different screen resolutions for backwards "
                            + "compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, and "
                            + "this lint check flags elements and attributes that are not fully supported. "
                            + "You should manually check whether the generated output is acceptable for "
                            + "those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_ANIMATED_VECTOR = "animated-vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";

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
        String tag = root.getTagName();
        if (!TAG_VECTOR.equals(tag) && !TAG_ANIMATED_VECTOR.equals(tag)) {
            return;
        }
        checkElement(context, root);
    }

    private static void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_GRADIENT.equals(tag)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Vector drawables with `<gradient>` are not rasterized correctly for older devices");
        } else if (TAG_CLIP_PATH.equals(tag)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<clip-path>` is not supported when rasterizing vector drawables for older devices");
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node node = attributes.item(i);
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                continue;
            }
            Attr attribute = (Attr) node;
            String name = attribute.getLocalName();
            if (name == null) {
                continue;
            }
            if (ATTR_FILL_TYPE.equals(name)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "`android:fillType` is only supported on API 24+ and may not be rasterized correctly");
            } else if (ATTR_AUTO_MIRRORED.equals(name)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "`android:autoMirrored` is not applied to generated bitmaps for older devices");
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull TextFormat textFormat) {
        int minSdk = context.getProject().getMinSdkVersion().getApiLevel();
        Object scope = incident.getScope();
        int requiredApi = 21;
        if (scope instanceof Element) {
            String tag = ((Element) scope).getTagName();
            if (TAG_GRADIENT.equals(tag)) {
                requiredApi = 24;
            }
        } else if (scope instanceof Attr) {
            Attr attribute = (Attr) scope;
            String name = attribute.getLocalName();
            if (ATTR_FILL_TYPE.equals(name)) {
                requiredApi = 24;
            }
        }
        return minSdk < requiredApi;
    }
}