package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import org.jetbrains.uast.UElement;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets.\n\n"
                            + "This finds cases where you have accidentally forgotten to add "
                            + "a widget to all variations of the layout, which could result "
                            + "in a runtime crash for some resource configurations when a "
                            + "`findViewById()` fails.\n\n"
                            + "There **are** cases where this is intentional. For example, you "
                            + "may have a dedicated large tablet layout which adds some extra "
                            + "widgets that are not present in the phone version of the layout. "
                            + "As long as the code accessing the layout resource is careful to "
                            + "handle this properly, it is valid. In that case, you can suppress "
                            + "this lint check for the given extra or missing views, or the whole "
                            + "layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final java.util.Map<String, java.util.List<LayoutInfo>> layoutMap =
            new java.util.HashMap<>();

    private static class LayoutInfo {
        final java.io.File file;
        final java.util.Set<String> ids = new java.util.HashSet<>();

        LayoutInfo(java.io.File file) {
            this.file = file;
        }
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Document document) {
        java.io.File file = context.file;
        String name = file.getName();
        int dot = name.indexOf('.');
        String layoutName = dot >= 0 ? name.substring(0, dot) : name;

        LayoutInfo info = new LayoutInfo(file);
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(context, root, info);
        }
        synchronized (layoutMap) {
            java.util.List<LayoutInfo> list = layoutMap.get(layoutName);
            if (list == null) {
                list = new java.util.ArrayList<>();
                layoutMap.put(layoutName, list);
            }
            list.add(info);
        }
    }

    private void collectIds(XmlContext context, org.w3c.dom.Element element, LayoutInfo info) {
        org.w3c.dom.Attr idAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            int index = idValue.indexOf('/');
            if (index >= 0) {
                String id = idValue.substring(index + 1);
                info.ids.add(id);
            }
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                collectIds(context, (org.w3c.dom.Element) child, info);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        for (java.util.Map.Entry<String, java.util.List<LayoutInfo>> entry : layoutMap.entrySet()) {
            java.util.List<LayoutInfo> variations = entry.getValue();
            if (variations.size() <= 1) {
                continue;
            }

            java.util.Set<String> allIds = new java.util.HashSet<>();
            for (LayoutInfo var : variations) {
                allIds.addAll(var.ids);
            }

            for (LayoutInfo var : variations) {
                for (String id : allIds) {
                    if (!var.ids.contains(id)) {
                        Location location = Location.create(var.file);
                        String message = String.format(
                                "Layout `%s` in `%s` is missing ID `%s` which is defined in other variations",
                                entry.getKey(), var.file.getParentFile().getName(), id);
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UElement node,
            @com.android.annotations.NonNull com.android.resources.ResourceType type,
            @com.android.annotations.NonNull String name,
            boolean isWrite) {
        // Required map check implementation from interface
    }
}