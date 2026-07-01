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
import org.jetbrains.uast.UElement;

public class LayoutConsistencyDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource"
                            + " folders specifies the same set of widgets. This finds cases where you"
                            + " have accidentally forgotten to add a widget to all variations of the"
                            + " layout, which could result in a runtime crash for some resource"
                            + " configurations when a findViewById() fails.\n"
                            + "\n"
                            + "There are cases where this is intentional. For example, you may have a"
                            + " dedicated large tablet layout which adds some extra widgets that are"
                            + " not present in the phone version of the layout. As long as the code"
                            + " accessing the layout resource is careful to handle this properly, it is"
                            + " valid. In that case, you can suppress this lint check for the given"
                            + " extra or missing views, or the whole layout.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            java.util.EnumSet.of(
                                    Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private static final class LayoutInfo {
        java.util.Set<String> ids = new java.util.HashSet<>();
        Location location;
    }

    private final java.util.Map<String, java.util.List<LayoutInfo>> mLayoutInfos =
            new java.util.HashMap<>();

    private final java.util.Map<String, java.util.List<Location>> mLayoutRefs =
            new java.util.HashMap<>();

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        LayoutInfo info = new LayoutInfo();
        collectIds(root, info.ids);
        info.location = context.getLocation(root);

        String name = context.file.getName();
        if (name.endsWith(".xml")) {
            name = name.substring(0, name.length() - ".xml".length());
        }

        mLayoutInfos
                .computeIfAbsent(name, k -> new java.util.ArrayList<>())
                .add(info);
    }

    @Override
    public boolean appliesToResourceRefs(com.android.resources.ResourceType type) {
        return type == com.android.resources.ResourceType.LAYOUT;
    }

    @Override
    public void visitResourceReference(
            JavaContext context,
            UElement node,
            com.android.resources.ResourceType type,
            String name,
            boolean isFramework) {
        if (isFramework) {
            return;
        }
        mLayoutRefs
                .computeIfAbsent(name, k -> new java.util.ArrayList<>())
                .add(context.getLocation(node));
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (java.util.Map.Entry<String, java.util.List<Location>> refEntry
                : mLayoutRefs.entrySet()) {
            String name = refEntry.getKey();
            java.util.List<LayoutInfo> infos = mLayoutInfos.get(name);
            if (infos == null || infos.size() < 2) {
                continue;
            }

            java.util.Set<String> referenceIds = infos.get(0).ids;
            boolean consistent = true;
            for (int i = 1; i < infos.size(); i++) {
                if (!infos.get(i).ids.equals(referenceIds)) {
                    consistent = false;
                    break;
                }
            }
            if (consistent) {
                continue;
            }

            for (LayoutInfo info : infos) {
                java.util.Set<String> missing = new java.util.HashSet<>(referenceIds);
                missing.removeAll(info.ids);
                java.util.Set<String> extra = new java.util.HashSet<>(info.ids);
                extra.removeAll(referenceIds);

                if (!missing.isEmpty() || !extra.isEmpty()) {
                    StringBuilder message = new StringBuilder();
                    message.append("This layout has a different set of views than other configurations of '")
                            .append(name)
                            .append("'");
                    if (!missing.isEmpty()) {
                        message.append(" (missing: ").append(missing).append(")");
                    }
                    if (!extra.isEmpty()) {
                        message.append(" (extra: ").append(extra).append(")");
                    }
                    message.append(
                            ". Accessing these views via findViewById() can cause a runtime crash.");
                    context.report(ISSUE, info.location, message.toString());
                }
            }
        }
    }

    private void collectIds(org.w3c.dom.Element element, java.util.Set<String> ids) {
        String id =
                element.getAttributeNS(
                        com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String idName = stripIdPrefix(id);
            if (idName != null) {
                ids.add(idName);
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                collectIds((org.w3c.dom.Element) child, ids);
            }
        }
    }

    private String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return null;
    }
}