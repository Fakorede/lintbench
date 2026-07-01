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
import com.android.tools.lint.detector.api.Location;
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
import java.util.Collection;
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
                            + "generation, and this lint check flags elements and attributes that "
                            + "are not fully supported. You should manually check whether the "
                            + "generated output is acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    // Elements not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ELEMENTS =
            new HashSet<>(
                    Arrays.asList(
                            "clip-path",
                            "group"
                    ));

    // Attributes not supported by the raster image generator
    private static final Set<String> UNSUPPORTED_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "fillType",
                            "trimPathStart",
                            "trimPathEnd",
                            "trimPathOffset"
                    ));

    // Attributes on <vector> root element that are not supported
    private static final Set<String> UNSUPPORTED_VECTOR_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "autoMirrored"
                    ));

    private static final String TAG_VECTOR = "vector";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

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

        // Check root vector element attributes
        checkUnsupportedAttributes(context, root, UNSUPPORTED_VECTOR_ATTRIBUTES);

        // Recursively check all child elements
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (UNSUPPORTED_ELEMENTS.contains(tagName)) {
                Location location = context.getLocation(childElement);
                String message =
                        String.format(
                                "This element (`%1$s`) is not supported for older versions of "
                                        + "the vector image generator; check that the output is "
                                        + "acceptable",
                                tagName);
                Incident incident = new Incident(ISSUE, childElement, location, message);
                context.report(incident, map());
            }

            checkUnsupportedAttributes(context, childElement, UNSUPPORTED_ATTRIBUTES);
            checkElement(context, childElement);
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
            Node attr = attrs.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getNodeName();
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (unsupportedAttrs.contains(localName)) {
                Location location = context.getLocation(attr);
                String message =
                        String.format(
                                "The attribute `%1$s` is not supported for older versions of "
                                        + "the vector image generator; check that the output is "
                                        + "acceptable",
                                localName);
                Incident incident = new Incident(ISSUE, attr, location, message);
                context.report(incident, map());
            }
        }
    }

    private static LintMap map() {
        return new LintMap();
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
}