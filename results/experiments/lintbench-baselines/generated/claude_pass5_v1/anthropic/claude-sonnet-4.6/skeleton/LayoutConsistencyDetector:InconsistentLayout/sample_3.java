package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
     * Map from layout base name to a map of folder qualifier -> set of view ids defined in that
     * layout variant.
     */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderIds = new HashMap<>();

    /**
     * Map from layout base name to a map of folder qualifier -> XmlContext (for location
     * reporting).
     */
    private final Map<String, Map<String, XmlContext>> mLayoutToFolderContext = new HashMap<>();

    /**
     * Set of layout names that are actually referenced via findViewById in Java/Kotlin code.
     * We only report issues for layouts that are actually accessed programmatically.
     */
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String layoutName = context.file.getName();
        // Strip the .xml extension
        if (layoutName.endsWith(".xml")) {
            layoutName = layoutName.substring(0, layoutName.length() - 4);
        }

        // Get the folder qualifier (e.g., "layout", "layout-land", "layout-large", etc.)
        String folderName = context.file.getParentFile().getName();

        // Collect all view IDs in this layout document
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store the ids for this layout variant
        Map<String, Set<String>> folderToIds =
                mLayoutToFolderIds.computeIfAbsent(layoutName, k -> new HashMap<>());
        folderToIds.put(folderName, ids);

        Map<String, XmlContext> folderToContext =
                mLayoutToFolderContext.computeIfAbsent(layoutName, k -> new HashMap<>());
        folderToContext.put(folderName, context);
    }

    /**
     * Recursively collect all android:id attribute values from the element tree.
     */
    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }

        String id = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            // Normalize: strip @+id/ or @id/ prefix
            if (id.startsWith("@+id/")) {
                id = id.substring(5);
            } else if (id.startsWith("@id/")) {
                id = id.substring(4);
            }
            if (!id.isEmpty()) {
                ids.add(id);
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
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        if (type == ResourceType.LAYOUT && !isFramework) {
            mReferencedLayouts.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // For each layout that appears in multiple folders, check consistency
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            // Only check layouts that appear in multiple folders
            if (folderToIds.size() < 2) {
                continue;
            }

            // Only check layouts that are actually referenced in code
            // (to avoid false positives for layouts only used via XML includes, etc.)
            // Actually, we check all multi-folder layouts as the spec says to find
            // cases where widgets are missing. We'll report regardless of Java references
            // since the spec doesn't restrict to only Java-referenced layouts.

            // Find the union of all IDs across all variants
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // If all variants have the same IDs, no issue
            boolean allSame = true;
            for (Set<String> ids : folderToIds.values()) {
                if (!ids.equals(allIds)) {
                    allSame = false;
                    break;
                }
            }

            if (allSame) {
                continue;
            }

            // Find the "base" folder (the one without qualifiers, i.e., just "layout")
            // to use as reference
            Map<String, XmlContext> folderToContext = mLayoutToFolderContext.get(layoutName);
            if (folderToContext == null) {
                continue;
            }

            // Report inconsistencies: for each folder, report IDs that are missing
            // compared to the union of all IDs
            List<String> folders = new ArrayList<>(folderToIds.keySet());
            Collections.sort(folders);

            for (String folder : folders) {
                Set<String> ids = folderToIds.get(folder);
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    XmlContext xmlContext = folderToContext.get(folder);
                    if (xmlContext == null) {
                        continue;
                    }

                    List<String> missingList = new ArrayList<>(missing);
                    Collections.sort(missingList);

                    // Find other folders that have the missing IDs
                    StringBuilder otherFolders = new StringBuilder();
                    for (String otherId : missingList) {
                        List<String> foldersWithId = new ArrayList<>();
                        for (Map.Entry<String, Set<String>> e2 : folderToIds.entrySet()) {
                            if (e2.getValue().contains(otherId)) {
                                foldersWithId.add(e2.getKey());
                            }
                        }
                        // Only report if referenced in Java code (optional - spec says
                        // it could result in runtime crash from findViewById)
                    }

                    String message = String.format(
                            "The id `%1$s` in layout `%2$s` is missing from the following "
                                    + "layout configurations: `%3$s`",
                            formatIds(missingList),
                            layoutName,
                            folder);

                    // Build a more descriptive message
                    message = buildMessage(layoutName, folder, missingList, folderToIds);

                    Location location = xmlContext.file != null
                            ? Location.create(xmlContext.file)
                            : Location.create(xmlContext.file);

                    xmlContext.report(ISSUE, location, message);
                }
            }
        }
    }

    private String buildMessage(
            String layoutName,
            String currentFolder,
            List<String> missingIds,
            Map<String, Set<String>> folderToIds) {

        // For each missing ID, find which other folders define it
        StringBuilder sb = new StringBuilder();
        sb.append("The layout `").append(layoutName).append("` in folder `")
                .append(currentFolder).append("` is missing the following ids that appear in other "
                        + "layout configurations: ");

        for (int i = 0; i < missingIds.size(); i++) {
            String id = missingIds.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("`").append(id).append("`");

            // Find folders that have this id
            List<String> foldersWithId = new ArrayList<>();
            for (Map.Entry<String, Set<String>> e : folderToIds.entrySet()) {
                if (!e.getKey().equals(currentFolder) && e.getValue().contains(id)) {
                    foldersWithId.add(e.getKey());
                }
            }
            if (!foldersWithId.isEmpty()) {
                Collections.sort(foldersWithId);
                sb.append(" (defined in ");
                for (int j = 0; j < foldersWithId.size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(foldersWithId.get(j));
                }
                sb.append(")");
            }
        }

        return sb.toString();
    }

    private String formatIds(List<String> ids) {
        if (ids.isEmpty()) return "";
        if (ids.size() == 1) return ids.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}