package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES));

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
                            + "layout",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /**
     * Map from layout name (e.g. "main") to a map of folder qualifier -> set of view IDs
     * e.g. "layout" -> { "id1", "id2" }
     *      "layout-land" -> { "id1" }
     */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderToIds = new HashMap<>();

    /**
     * Map from layout name to a map of folder qualifier -> XmlContext (for location reporting)
     */
    private final Map<String, Map<String, XmlContext>> mLayoutToFolderToContext = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Get the layout file name (without extension)
        String layoutName = context.file.getName();
        int dotIndex = layoutName.lastIndexOf('.');
        if (dotIndex != -1) {
            layoutName = layoutName.substring(0, dotIndex);
        }

        // Get the folder qualifier (e.g. "layout", "layout-land", "layout-large")
        String folderName = context.file.getParentFile().getName();

        // Collect all view IDs in this layout
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store the IDs for this layout variant
        Map<String, Set<String>> folderToIds = mLayoutToFolderToIds.get(layoutName);
        if (folderToIds == null) {
            folderToIds = new HashMap<>();
            mLayoutToFolderToIds.put(layoutName, folderToIds);
        }
        folderToIds.put(folderName, ids);

        // Store the context for location reporting
        Map<String, XmlContext> folderToContext = mLayoutToFolderToContext.get(layoutName);
        if (folderToContext == null) {
            folderToContext = new HashMap<>();
            mLayoutToFolderToContext.put(layoutName, folderToContext);
        }
        folderToContext.put(folderName, context);
    }

    /**
     * Recursively collect all android:id attribute values from elements in the layout.
     */
    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            Node idAttr = attributes.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android", "id");
            if (idAttr != null) {
                String idValue = idAttr.getNodeValue();
                if (idValue != null && !idValue.isEmpty()) {
                    // Normalize: strip @+id/ or @id/ prefix
                    if (idValue.startsWith("@+id/")) {
                        idValue = idValue.substring(5);
                    } else if (idValue.startsWith("@id/")) {
                        idValue = idValue.substring(4);
                    }
                    ids.add(idValue);
                }
            }
        }

        NodeList children = element.getChildNodes();
        if (children != null) {
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    collectIds((Element) child, ids);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // For each layout that appears in multiple folders, check consistency
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderToIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            if (folderToIds.size() < 2) {
                // Only one variant, nothing to compare
                continue;
            }

            Map<String, XmlContext> folderToContext = mLayoutToFolderToContext.get(layoutName);

            // Compute the union of all IDs across all folder variants
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            if (allIds.isEmpty()) {
                // No IDs at all, nothing to check
                continue;
            }

            // For each folder variant, check if it's missing any IDs
            // Sort folder names for deterministic output
            List<String> folders = new ArrayList<>(folderToIds.keySet());
            Collections.sort(folders);

            for (String folder : folders) {
                Set<String> ids = folderToIds.get(folder);
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(ids);

                if (!missingIds.isEmpty()) {
                    XmlContext xmlContext = folderToContext.get(folder);
                    if (xmlContext == null) {
                        continue;
                    }

                    // Build a sorted list of missing IDs for a deterministic message
                    List<String> sortedMissing = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissing);

                    // Find which folders DO have these IDs
                    List<String> otherFolders = new ArrayList<>();
                    for (String otherFolder : folders) {
                        if (!otherFolder.equals(folder)) {
                            Set<String> otherIds = folderToIds.get(otherFolder);
                            // Check if other folder has at least one of the missing IDs
                            for (String missingId : missingIds) {
                                if (otherIds.contains(missingId)) {
                                    otherFolders.add(otherFolder);
                                    break;
                                }
                            }
                        }
                    }

                    String message = String.format(
                            "The id `%1$s` in layout `%2$s` is missing from the following layout "
                                    + "configurations: `%3$s`",
                            formatIds(sortedMissing),
                            layoutName,
                            folder);

                    // Report on the document location
                    Document doc = xmlContext.document;
                    Location location = xmlContext.getLocation(doc.getDocumentElement());
                    xmlContext.report(ISSUE, doc.getDocumentElement(), location, message);
                }
            }
        }
    }

    private static String formatIds(@NonNull List<String> ids) {
        if (ids.isEmpty()) {
            return "";
        }
        if (ids.size() == 1) {
            return ids.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = ids.size(); i < n; i++) {
            if (i > 0) {
                if (i == n - 1) {
                    sb.append(", ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UElement node,
            @NonNull String type,
            @NonNull String name,
            boolean isFramework) {
        // Not used in this detector; resource reference analysis is handled
        // via XML document traversal in visitDocument and afterCheckRootProject.
    }
}