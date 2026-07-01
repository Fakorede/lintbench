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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    // Attributes on <vector> root element not supported
    private static final Set<String> UNSUPPORTED_ROOT_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "android:alpha"
                    ));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_GRADIENT = "gradient";
    private static final String TAG_AAPT_ATTR = "aapt:attr";

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

        List<String> problems = new ArrayList<>();
        List<Location> locations = new ArrayList<>();

        // Check root element attributes
        checkAttributes(context, root, problems, locations);

        // Recursively check child elements
        checkElement(context, root, problems, locations);

        if (!problems.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(
                    "This vector drawable includes elements or attributes not supported by "
                            + "the image generation. ");

            if (problems.size() == 1) {
                message.append("Issue found: ").append(problems.get(0));
            } else {
                message.append("Issues found:\n");
                for (String problem : problems) {
                    message.append(" * ").append(problem).append("\n");
                }
            }

            Location location = locations.isEmpty()
                    ? context.getLocation(root)
                    : locations.get(0);

            // Link additional locations
            if (locations.size() > 1) {
                Location prev = null;
                for (int i = locations.size() - 1; i >= 1; i--) {
                    Location loc = locations.get(i);
                    loc.setSecondary(prev);
                    prev = loc;
                }
                location.setSecondary(prev);
            }

            Incident incident = new Incident(ISSUE, root, location, message.toString());
            context.report(incident, map());
        }
    }

    private LintMap map() {
        return new LintMap();
    }

    private void checkElement(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull List<String> problems,
            @NonNull List<Location> locations) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            // Check for unsupported elements
            if (TAG_GRADIENT.equals(tagName)) {
                String msg = "Gradient support in vector drawables requires API 24 or higher; "
                        + "the rasterized image generated for older devices will not include the gradient";
                problems.add(msg);
                locations.add(context.getLocation(childElement));
            } else if (TAG_AAPT_ATTR.equals(tagName)) {
                String msg = "The `aapt:attr` element is not supported in vector drawables "
                        + "for raster image generation";
                problems.add(msg);
                locations.add(context.getLocation(childElement));
            } else if (UNSUPPORTED_ELEMENTS.contains(tagName)) {
                String msg = "The `<" + tagName + ">` element is not fully supported "
                        + "by the raster image generation";
                problems.add(msg);
                locations.add(context.getLocation(childElement));
            }

            // Check attributes of child element
            checkAttributes(context, childElement, problems, locations);

            // Recurse into children
            checkElement(context, childElement, problems, locations);
        }
    }

    private void checkAttributes(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull List<String> problems,
            @NonNull List<Location> locations) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }
        String tagName = element.getTagName();
        boolean isRoot = TAG_VECTOR.equals(tagName);

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();

            if (isRoot && UNSUPPORTED_ROOT_ATTRIBUTES.contains(attrName)) {
                String msg = "The `" + attrName + "` attribute on the `<vector>` element "
                        + "is not supported by the raster image generation";
                problems.add(msg);
                locations.add(context.getLocation(attr));
            } else if (UNSUPPORTED_ATTRIBUTES.contains(attrName)) {
                String msg = "The `" + attrName + "` attribute is not supported "
                        + "by the raster image generation";
                problems.add(msg);
                locations.add(context.getLocation(attr));
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull LintMap map) {
        // Only report the incident if the minSdkVersion is less than 21
        if (context.getMainProject().getMinSdk() >= 21) {
            return false;
        }
        return true;
    }
}