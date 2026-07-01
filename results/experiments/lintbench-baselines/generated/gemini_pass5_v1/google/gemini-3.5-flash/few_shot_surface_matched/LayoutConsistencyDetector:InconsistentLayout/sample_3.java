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
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UElement;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

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
                            + "There are cases where this is intentional. For example, you "
                            + "may have a dedicated large tablet layout which adds some extra "
                            + "widgets that are not present in the phone version of the layout. "
                            + "As long as the code accessing the layout resource is careful to "
                            + "handle this properly, it is valid. In that case, you can suppress "
                            + "this lint check for the given extra or missing views, or the "
                            + "whole layout",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    private final Map<String, List<LayoutFileRecord>> layoutRecords = new HashMap<>();

    private static class LayoutFileRecord {
        final String folderName;
        final File file;
        final Set<String> ids;
        final Location location;

        LayoutFileRecord(String folderName, File file, Set<String> ids, Location location) {
            this.folderName = folderName;
            this.file = file;
            this.ids = ids;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Document document) {
        String layoutName = context.file.getName();
        if (layoutName.endsWith(".xml")) {
            layoutName = layoutName.substring(0, layoutName.length() - 4);
        }
        String folderName = context.file.getParentFile().getName();
        Set<String> ids = new HashSet<>();
        if (document.getDocumentElement() != null) {
            collectIds(document.getDocumentElement(), ids);
        }
        Location location = context.getLocation(document.getDocumentElement() != null ? document.getDocumentElement() : document);

        synchronized (layoutRecords) {
            List<LayoutFileRecord> records = layoutRecords.get(layoutName);
            if (records == null) {
                records = new ArrayList<>();
                layoutRecords.put(layoutName, records);
            }
            records.add(new LayoutFileRecord(folderName, context.file, ids, location));
        }
    }

    private void collectIds(org.w3c.dom.Element element, Set<String> ids) {
        if (element == null) return;
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            int index = id.indexOf('/');
            if (index != -1) {
                ids.add(id.substring(index + 1));
            } else {
                ids.add(id);
            }
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                collectIds((org.w3c.dom.Element) child, ids);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        for (Map.Entry<String, List<LayoutFileRecord>> entry : layoutRecords.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutFileRecord> records = entry.getValue();
            if (records.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutFileRecord record : records) {
                allIds.addAll(record.ids);
            }

            for (LayoutFileRecord record : records) {
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(record.ids);

                if (!missingIds.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Layout \"").append(layoutName).append("\" in \"").append(record.folderName)
                      .append("\" is missing the following IDs defined in other variations: ");
                    boolean first = true;
                    for (String missingId : missingIds) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append(missingId);
                        first = false;
                    }

                    context.report(ISSUE, record.location, sb.toString());
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
            boolean isFramework) {
        // Required override, satisfies the scanner interface specification
    }
}