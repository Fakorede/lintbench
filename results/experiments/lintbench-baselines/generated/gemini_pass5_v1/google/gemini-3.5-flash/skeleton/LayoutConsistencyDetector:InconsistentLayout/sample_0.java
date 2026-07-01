package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.EnumSet;
import org.w3c.dom.Document;

public class LayoutConsistencyDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets. This finds cases "
                            + "where you have accidentally forgotten to add a widget to all variations "
                            + "of the layout, which could result in a runtime crash for some resource "
                            + "configurations when a `findViewById()` fails.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final java.util.Map<String, java.util.List<LayoutFile>> layouts = new java.util.HashMap<>();

    private static class LayoutFile {
        final java.io.File file;
        final com.android.tools.lint.detector.api.Location location;
        final java.util.Set<String> ids;

        LayoutFile(java.io.File file, com.android.tools.lint.detector.api.Location location, java.util.Set<String> ids) {
            this.file = file;
            this.location = location;
            this.ids = ids;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);

        java.util.Set<String> ids = new java.util.HashSet<>();
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(root, ids);
        }

        com.android.tools.lint.detector.api.Location location = context.getLocation(root != null ? root : document);
        LayoutFile layoutFile = new LayoutFile(context.file, location, ids);

        synchronized (layouts) {
            java.util.List<LayoutFile> files = layouts.get(layoutName);
            if (files == null) {
                files = new java.util.ArrayList<>();
                layouts.put(layoutName, files);
            }
            files.add(layoutFile);
        }
    }

    private void collectIds(org.w3c.dom.Node node, java.util.Set<String> ids) {
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
            if (id != null && !id.isEmpty()) {
                String cleanId = stripIdPrefix(id);
                if (!cleanId.isEmpty()) {
                    ids.add(cleanId);
                }
            }
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectIds(children.item(i), ids);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (java.util.Map.Entry<String, java.util.List<LayoutFile>> entry : layouts.entrySet()) {
            java.util.List<LayoutFile> files = entry.getValue();
            if (files.size() <= 1) {
                continue;
            }

            java.util.Set<String> allIds = new java.util.HashSet<>();
            for (LayoutFile file : files) {
                allIds.addAll(file.ids);
            }

            for (LayoutFile file : files) {
                java.util.Set<String> missingIds = new java.util.HashSet<>(allIds);
                missingIds.removeAll(file.ids);

                if (!missingIds.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Layout ").append(entry.getKey()).append(" in ")
                      .append(file.file.getParentFile().getName())
                      .append(" is missing the following IDs: ");

                    boolean first = true;
                    for (String missingId : missingIds) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append(missingId);
                        first = false;
                    }

                    context.report(
                        ISSUE,
                        file.location,
                        sb.toString()
                    );
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Override
    public void visitResourceReference(
            @NonNull com.android.tools.lint.detector.api.JavaContext context,
            @NonNull org.jetbrains.uast.UElement node,
            @NonNull com.android.resources.ResourceType type,
            @NonNull String name,
            boolean isGrowable) {
        // No-op
    }
}