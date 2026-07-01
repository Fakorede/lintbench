package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle "
                            + "plugin 1.4 or higher is used, a vector drawable placed in the "
                            + "`drawable` folder is automatically moved to `drawable-anydpi-v21` "
                            + "or `drawable-anydpi-v24` and bitmap images are generated for "
                            + "different screen resolutions for backwards compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image generation, "
                            + "and this lint check flags elements and attributes that are not fully "
                            + "supported. You should manually check whether the generated output is "
                            + "acceptable for those older devices.",
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

    // Attributes not supported by the vector rasterizer (on any element)
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

    // Attributes on <vector> root element that are not supported
    private static final Set<String> UNSUPPORTED_ROOT_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            "android:autoMirrored",
                            "android:tint",
                            "android:tintMode",
                            "android:alpha"
                    ));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_VECTOR = "vector";

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

        // Check root attributes
        checkAttributes(context, root, true);

        // Walk all child elements
        walkElements(context, root);
    }

    private void walkElements(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();

                if (UNSUPPORTED_ELEMENTS.contains(tagName)) {
                    reportIssue(
                            context,
                            childElement,
                            context.getLocation(childElement),
                            "This element or attribute is not supported in images "
                                    + "generated from this vector icon for API < 21; "
                                    + "check generated icon");
                }

                checkAttributes(context, childElement, false);
                walkElements(context, childElement);
            }
        }
    }

    private void checkAttributes(
            @NonNull XmlContext context, @NonNull Element element, boolean isRoot) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String qualifiedName = attr.getName();
            String localName = attr.getLocalName();
            String ns = attr.getNamespaceURI();

            if (ANDROID_NS.equals(ns)) {
                String fullName = "android:" + localName;

                if (UNSUPPORTED_ATTRIBUTES.contains(fullName)) {
                    reportIssue(
                            context,
                            attr,
                            context.getLocation(attr),
                            "This element or attribute is not supported in images "
                                    + "generated from this vector icon for API < 21; "
                                    + "check generated icon");
                } else if (isRoot && UNSUPPORTED_ROOT_ATTRIBUTES.contains(fullName)) {
                    reportIssue(
                            context,
                            attr,
                            context.getLocation(attr),
                            "This element or attribute is not supported in images "
                                    + "generated from this vector icon for API < 21; "
                                    + "check generated icon");
                }
            }
        }
    }

    private void reportIssue(
            @NonNull XmlContext context,
            @NonNull Node node,
            @NonNull Location location,
            @NonNull String message) {
        Incident incident = new Incident(ISSUE, node, location, message);
        context.report(incident);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context,
            @NonNull Incident incident,
            @NonNull com.android.tools.lint.detector.api.LintMap map) {
        // Only report if minSdkVersion < 21
        if (context.getMainProject().getMinSdk() >= 21) {
            return false;
        }
        return true;
    }
}