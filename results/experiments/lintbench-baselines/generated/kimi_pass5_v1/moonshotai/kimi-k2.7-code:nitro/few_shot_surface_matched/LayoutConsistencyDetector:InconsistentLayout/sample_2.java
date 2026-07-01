package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders specifies the same set of widgets. This finds cases "
                            + "where you have accidentally forgotten to add a widget to all "
                            + "variations of the layout, which could result in a runtime crash for "
                            + "some resource configurations when a findViewById() fails.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private final Map<String, Map<String, LayoutVariant>> mLayoutMap = new HashMap<>();
    private final Set<String> mReferencedLayouts = new HashSet<>();

    private static class LayoutVariant {
        final File file;
        final Set<String> ids;

        LayoutVariant(File file, Set<String> ids) {
            this.file = file;
            this.ids = ids;
        }
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        File file = context.file;
        String folder = file.getParentFile().getName();
        String resourceName = getBaseName(file.getName());

        Map<String, LayoutVariant> variants = mLayoutMap.get(resourceName);
        if (variants == null) {
            variants = new HashMap<>();
            mLayoutMap.put(resourceName, variants);
        }
        variants.put(folder, new LayoutVariant(file, ids));
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    private static void collectIds(Element element, Set<String> ids) {
        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                if (attr.getNodeType() != Node.ATTRIBUTE_NODE) {
                    continue;
                }
                if (!"id".equals(attr.getLocalName()) && !"id".equals(attr.getNodeName())) {
                    continue;
                }
                String value = attr.getNodeValue();
                if (value == null) {
                    continue;
                }
                int slash = value.lastIndexOf('/');
                if (slash >= 0) {
                    ids.add(value.substring(slash + 1));
                } else {
                    ids.add(value);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            JavaContext context, UElement node, String type, String name, boolean isFramework) {
        if (!isFramework && "layout".equals(type)) {
            mReferencedLayouts.add(name);
        }
    }

    @Override
    public void visitResourceReference(
            JavaContext context,
            UElement node,
            com.android.resources.ResourceType type,
            String name,
            boolean isFramework) {
        if (!isFramework && type == com.android.resources.ResourceType.LAYOUT) {
            mReferencedLayouts.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, Map<String, LayoutVariant>> entry : mLayoutMap.entrySet()) {
            String layoutName = entry.getKey();
            if (!mReferencedLayouts.contains(layoutName)) {
                continue;
            }

            Map<String, LayoutVariant> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutVariant variant : variants.values()) {
                allIds.addAll(variant.ids);
            }

            if (allIds.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, LayoutVariant> variantEntry : variants.entrySet()) {
                String folder = variantEntry.getKey();
                LayoutVariant variant = variantEntry.getValue();

                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(variant.ids);
                if (missing.isEmpty()) {
                    continue;
                }

                List<String> sorted = new ArrayList<>(missing);
                Collections.sort(sorted);

                context.report(
                        ISSUE,
                        Location.create(variant.file),
                        "The layout \""
                                + layoutName
                                + "\" in "
                                + folder
                                + " is missing widgets that are present in other configurations: "
                                + sorted);
            }
        }

        mLayoutMap.clear();
        mReferencedLayouts.clear();
    }
}