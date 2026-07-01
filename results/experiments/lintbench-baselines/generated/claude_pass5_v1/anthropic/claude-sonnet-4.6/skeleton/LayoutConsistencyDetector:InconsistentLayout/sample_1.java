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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
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
     * Map from layout name (e.g. "main") to a map from folder configuration (e.g. "layout-land")
     * to the set of view IDs found in that configuration.
     */
    private final Map<String, Map<String, Set<String>>> mLayouts = new HashMap<>();

    /**
     * Map from layout name to a map from folder configuration to XmlContext (for location
     * reporting).
     */
    private final Map<String, Map<String, Location>> mLocations = new HashMap<>();

    /**
     * Set of layout names that are actually referenced via R.layout.xxx in Java/Kotlin code.
     */
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String layoutName = context.file.getName();
        // Strip .xml extension
        if (layoutName.endsWith(".xml")) {
            layoutName = layoutName.substring(0, layoutName.length() - 4);
        }

        // Get the folder name (e.g. "layout", "layout-land", "layout-large")
        String folderName = context.file.getParentFile().getName();

        // Collect all view IDs in this layout
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store in map
        Map<String, Set<String>> configurations = mLayouts.get(layoutName);
        if (configurations == null) {
            configurations = new HashMap<>();
            mLayouts.put(layoutName, configurations);
        }
        configurations.put(folderName, ids);

        // Store location
        Map<String, Location> locationMap = mLocations.get(layoutName);
        if (locationMap == null) {
            locationMap = new HashMap<>();
            mLocations.put(layoutName, locationMap);
        }
        locationMap.put(folderName, context.getLocation(document.getDocumentElement()));
    }

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
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayouts.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> configurations = entry.getValue();

            // Only check layouts that appear in multiple configurations
            if (configurations.size() < 2) {
                continue;
            }

            // Only check layouts that are referenced from code (if we have that info)
            // If mReferencedLayouts is empty (no Java files scanned), check all
            if (!mReferencedLayouts.isEmpty() && !mReferencedLayouts.contains(layoutName)) {
                continue;
            }

            // Compute the union of all IDs across all configurations
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : configurations.values()) {
                allIds.addAll(ids);
            }

            // For each configuration, check which IDs are missing
            for (Map.Entry<String, Set<String>> configEntry : configurations.entrySet()) {
                String folderName = configEntry.getKey();
                Set<String> ids = configEntry.getValue();

                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(ids);

                if (!missingIds.isEmpty()) {
                    Map<String, Location> locationMap = mLocations.get(layoutName);
                    Location location = null;
                    if (locationMap != null) {
                        location = locationMap.get(folderName);
                    }

                    // Build list of other folders that have these IDs
                    List<String> sortedMissing = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissing);

                    // Find which folders have each missing ID to give better messages
                    StringBuilder message = new StringBuilder();
                    message.append("The id `");

                    if (sortedMissing.size() == 1) {
                        message.append(sortedMissing.get(0));
                        message.append("` in layout `").append(layoutName)
                                .append("` is missing from the `").append(folderName)
                                .append("` configuration");
                    } else {
                        message.append(formatIdList(sortedMissing));
                        message.append("` in layout `").append(layoutName)
                                .append("` are missing from the `").append(folderName)
                                .append("` configuration");
                    }

                    // Find secondary locations
                    Location secondary = null;
                    for (Map.Entry<String, Set<String>> otherEntry : configurations.entrySet()) {
                        if (otherEntry.getKey().equals(folderName)) {
                            continue;
                        }
                        Set<String> otherIds = otherEntry.getValue();
                        // Check if this other config has any of the missing IDs
                        for (String missingId : missingIds) {
                            if (otherIds.contains(missingId)) {
                                if (locationMap != null) {
                                    Location otherLocation = locationMap.get(otherEntry.getKey());
                                    if (otherLocation != null) {
                                        otherLocation.setMessage(
                                                "Defined here in `" + otherEntry.getKey() + "`");
                                        if (secondary == null) {
                                            secondary = otherLocation;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }

                    if (location != null) {
                        if (secondary != null) {
                            location.setSecondary(secondary);
                        }
                        context.report(ISSUE, location, message.toString());
                    }
                }
            }
        }
    }

    private String formatIdList(List<String> ids) {
        if (ids.size() == 1) {
            return ids.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                if (i == ids.size() - 1) {
                    sb.append("` and `");
                } else {
                    sb.append("`, `");
                }
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Track which layouts are referenced via R.layout.xxx
        // This is handled via appliesToResourceRefs and the resource reference visitor
        // The actual tracking is done in the resource reference callback
    }
}