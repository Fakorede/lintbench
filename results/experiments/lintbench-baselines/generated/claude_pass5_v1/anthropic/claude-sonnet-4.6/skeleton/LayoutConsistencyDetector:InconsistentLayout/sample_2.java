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
import java.util.Arrays;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets.\n"
                            + "\n"
                            + "This finds cases where you have accidentally forgotten to add "
                            + "a widget to all variations of the layout, which could result "
                            + "in a runtime crash for some resource configurations when a "
                            + "`findViewById()` fails.\n"
                            + "\n"
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
     * Map from layout base name to a map of folder configuration to list of view ids in that
     * folder's version of the layout.
     */
    private final Map<String, Map<String, Set<String>>> mLayouts = new HashMap<>();

    /**
     * Map from layout base name to a map of folder configuration to the XmlContext location (file
     * location) for that layout variant.
     */
    private final Map<String, Map<String, Location>> mLocations = new HashMap<>();

    /**
     * Set of layout names that are actually referenced via R.layout.xxx in Java/Kotlin code, so we
     * only report issues for layouts that are actually used.
     */
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String folderName = context.file.getParentFile().getName();
        String layoutName = context.file.getName();
        // Remove extension
        int dot = layoutName.lastIndexOf('.');
        if (dot != -1) {
            layoutName = layoutName.substring(0, dot);
        }

        // Collect all view ids in this layout
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store the ids for this layout variant
        Map<String, Set<String>> variants = mLayouts.get(layoutName);
        if (variants == null) {
            variants = new HashMap<>();
            mLayouts.put(layoutName, variants);
        }
        variants.put(folderName, ids);

        // Store location
        Map<String, Location> locations = mLocations.get(layoutName);
        if (locations == null) {
            locations = new HashMap<>();
            mLocations.put(layoutName, locations);
        }
        locations.put(folderName, context.getLocation(document.getDocumentElement()));
    }

    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            Node idAttr = attributes.getNamedItemNS(ANDROID_NS, ATTR_ID);
            if (idAttr != null) {
                String id = idAttr.getNodeValue();
                if (id != null && !id.isEmpty()) {
                    // Normalize the id: strip @+id/ or @id/ prefix
                    if (id.startsWith("@+id/")) {
                        id = id.substring(5);
                    } else if (id.startsWith("@id/")) {
                        id = id.substring(4);
                    }
                    ids.add(id);
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
        // For each layout that has multiple variants, check consistency
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayouts.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> variants = entry.getValue();

            // Only check layouts with multiple variants
            if (variants.size() < 2) {
                continue;
            }

            // Only report issues for layouts that are actually referenced (or report all)
            // The spec doesn't restrict to referenced layouts, so we check all.

            // Find the union of all ids across all variants
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : variants.values()) {
                allIds.addAll(ids);
            }

            // If all variants have the same ids, no problem
            boolean allSame = true;
            for (Set<String> ids : variants.values()) {
                if (!ids.equals(allIds)) {
                    allSame = false;
                    break;
                }
            }

            if (allSame) {
                continue;
            }

            // Find folders that are missing some ids
            // Sort folder names for deterministic output
            List<String> folderNames = new ArrayList<>(variants.keySet());
            Collections.sort(folderNames);

            Map<String, Location> locations = mLocations.get(layoutName);

            for (String folderName : folderNames) {
                Set<String> ids = variants.get(folderName);
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    // Build message
                    List<String> missingList = new ArrayList<>(missing);
                    Collections.sort(missingList);

                    // Find which folders have these ids
                    StringBuilder otherFolders = new StringBuilder();
                    for (String other : folderNames) {
                        if (other.equals(folderName)) {
                            continue;
                        }
                        Set<String> otherIds = variants.get(other);
                        for (String m : missingList) {
                            if (otherIds.contains(m)) {
                                if (otherFolders.length() > 0) {
                                    otherFolders.append(", ");
                                }
                                otherFolders.append(other);
                                break;
                            }
                        }
                    }

                    String message =
                            String.format(
                                    "The id `%1$s` in layout `%2$s` is missing from the following layout configurations: `%3$s`",
                                    formatIdList(missingList),
                                    layoutName,
                                    folderName);

                    // Build a more accurate message similar to the real lint check
                    message =
                            String.format(
                                    "Layout `%1$s` has different widget sets in different configurations: Layout file in `%2$s` is missing: %3$s",
                                    layoutName,
                                    folderName,
                                    formatIdList(missingList));

                    Location location = null;
                    if (locations != null) {
                        location = locations.get(folderName);
                    }

                    if (location == null) {
                        context.report(ISSUE, location, message);
                    } else {
                        // Add secondary locations for the other variants
                        Location secondary = null;
                        for (String other : folderNames) {
                            if (other.equals(folderName)) {
                                continue;
                            }
                            if (locations != null) {
                                Location otherLocation = locations.get(other);
                                if (otherLocation != null) {
                                    otherLocation.setMessage("Defined here");
                                    if (secondary == null) {
                                        secondary = otherLocation;
                                    } else {
                                        // Chain locations
                                        Location last = secondary;
                                        while (last.getSecondary() != null) {
                                            last = last.getSecondary();
                                        }
                                        last.setSecondary(otherLocation);
                                    }
                                }
                            }
                        }
                        if (secondary != null) {
                            location.setSecondary(secondary);
                        }
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    private static String formatIdList(@NonNull List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = ids.size(); i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('`').append(ids.get(i)).append('`');
        }
        return sb.toString();
    }
}