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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private static final Set<String> UNSUPPORTED_ELEMENTS =
            new HashSet<>(
                    Arrays.asList(
                            "clip-path",
                            "group"
                    ));

    // Attributes not supported by the vector rasterizer
    private static final Set<String> UNSUPPORTED_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "android:fillType",
                            "android:strokeMiterLimit",
                            "android:strokeLineCap",
                            "android:strokeLineJoin",
                            "android:trimPathStart",
                            "android:trimPathEnd",
                            "android:trimPathOffset"
                    ));

    // Attributes on <vector> that are not supported
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "android:alpha"
                    ));

    private static final String TAG_VECTOR = "vector";
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

        if (!TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        // Only flag issues when the file is in the plain "drawable" folder (no qualifiers
        // that would restrict it to API 21+), and when minSdkVersion < 21.
        // We use filterIncident to conditionally suppress based on minSdkVersion.

        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (UNSUPPORTED_ELEMENTS.contains(tagName)) {
            String message =
                    "This element (`"
                            + tagName
                            + "`) is not supported in images generated from this "
                            + "vector icon for API < 21; check generated icon to verify";
            Incident incident =
                    new Incident(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            message,
                            null);
            context.report(incident, map());
        }

        // Check attributes on this element
        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                String attrName = attr.getNodeName();

                boolean unsupported = false;
                if (UNSUPPORTED_ATTRIBUTES.contains(attrName)) {
                    unsupported = true;
                } else if (TAG_VECTOR.equals(tagName)
                        && UNSUPPORTED_VECTOR_ATTRIBUTES.contains(attrName)) {
                    unsupported = true;
                }

                if (unsupported) {
                    String message =
                            "The attribute `"
                                    + attrName
                                    + "` is not supported in images generated from this "
                                    + "vector icon for API < 21; check generated icon to verify";
                    Incident incident =
                            new Incident(
                                    ISSUE,
                                    (org.w3c.dom.Attr) attr,
                                    context.getLocation((org.w3c.dom.Attr) attr),
                                    message,
                                    null);
                    context.report(incident, map());
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

    @NonNull
    private static LintMap map() {
        return new LintMap();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Only report the issue if minSdkVersion < 21
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk >= 21) {
            return false; // suppress
        }
        return true; // report
    }
}