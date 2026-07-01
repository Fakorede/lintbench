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
import com.android.tools.lint.detector.api.ResourceEvaluator;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
     * Map from layout name to a map of folder qualifier -> set of view ids in that folder's
     * version of the layout.
     */
    private final Map<String, Map<String, Set<String>>> mLayouts = new HashMap<>();

    /**
     * Map from layout name to a map of folder qualifier -> XmlContext for location reporting.
     */
    private final Map<String, Map<String, XmlContext>> mContexts = new HashMap<>();

    /**
     * Map from layout name to a map of folder qualifier -> map of id -> Location.
     */
    private final Map<String, Map<String, Map<String, Location>>> mLocations = new HashMap<>();

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

        String folderName = context.file.getParentFile().getName();

        // Collect all ids in this layout
        Set<String> ids = new HashSet<>();
        Map<String, Location> idLocations = new HashMap<>();
        collectIds(document.getDocumentElement(), ids, idLocations, context);

        // Store in the layout map
        Map<String, Set<String>> folderMap = mLayouts.get(layoutName);
        if (folderMap == null) {
            folderMap = new HashMap<>();
            mLayouts.put(layoutName, folderMap);
        }
        folderMap.put(folderName, ids);

        // Store contexts
        Map<String, XmlContext> contextMap = mContexts.get(layoutName);
        if (contextMap == null) {
            contextMap = new HashMap<>();
            mContexts.put(layoutName, contextMap);
        }
        contextMap.put(folderName, context);

        // Store locations
        Map<String, Map<String, Location>> locMap = mLocations.get(layoutName);
        if (locMap == null) {
            locMap = new HashMap<>();
            mLocations.put(layoutName, locMap);
        }
        locMap.put(folderName, idLocations);
    }

    private void collectIds(
            @Nullable Element element,
            @NonNull Set<String> ids,
            @NonNull Map<String, Location> locations,
            @NonNull XmlContext context) {
        if (element == null) {
            return;
        }

        // Check for android:id attribute
        String id = element.getAttributeNS(ANDROID_NS, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            // Normalize the id (strip @+id/ or @id/ prefix)
            String normalizedId = normalizeId(id);
            if (normalizedId != null && !normalizedId.isEmpty()) {
                ids.add(normalizedId);
                locations.put(normalizedId, context.getLocation(element));
            }
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                collectIds((Element) child, ids, locations, context);
            }
        }
    }

    @Nullable
    private static String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // For each layout that appears in multiple folders, check consistency
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayouts.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            // Only check layouts that appear in multiple folders
            if (folderToIds.size() < 2) {
                continue;
            }

            // Find the "base" layout (the one in the plain "layout" folder)
            // or use all folders for comparison
            List<String> folders = new ArrayList<>(folderToIds.keySet());
            Collections.sort(folders);

            // Find the union of all ids across all folders
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // For each folder, check if it's missing any ids
            Map<String, XmlContext> contextMap = mContexts.get(layoutName);
            Map<String, Map<String, Location>> locMap = mLocations.get(layoutName);

            if (contextMap == null || locMap == null) {
                continue;
            }

            // Determine the "reference" folder - prefer plain "layout"
            String referenceFolder = null;
            for (String folder : folders) {
                if (folder.equals("layout")) {
                    referenceFolder = folder;
                    break;
                }
            }
            if (referenceFolder == null) {
                referenceFolder = folders.get(0);
            }

            Set<String> referenceIds = folderToIds.get(referenceFolder);

            for (String folder : folders) {
                if (folder.equals(referenceFolder)) {
                    continue;
                }

                Set<String> folderIds = folderToIds.get(folder);
                XmlContext folderContext = contextMap.get(folder);
                Map<String, Location> folderLocations = locMap.get(folder);

                if (folderIds == null || folderContext == null || folderLocations == null) {
                    continue;
                }

                // Find ids in reference but not in this folder (missing)
                Set<String> missingIds = new HashSet<>(referenceIds);
                missingIds.removeAll(folderIds);

                // Find ids in this folder but not in reference (extra)
                Set<String> extraIds = new HashSet<>(folderIds);
                extraIds.removeAll(referenceIds);

                if (!missingIds.isEmpty() || !extraIds.isEmpty()) {
                    // Report the issue on the folder variant
                    StringBuilder message = new StringBuilder();
                    message.append("The `").append(folder).append("/").append(layoutName)
                            .append("` layout is missing the following IDs that were found in the `")
                            .append(referenceFolder).append("` layout: ");

                    if (!missingIds.isEmpty()) {
                        List<String> sortedMissing = new ArrayList<>(missingIds);
                        Collections.sort(sortedMissing);
                        message.append("`").append(formatIds(sortedMissing)).append("`");
                    }

                    if (!extraIds.isEmpty()) {
                        if (!missingIds.isEmpty()) {
                            message.append(" and has extra IDs: ");
                        } else {
                            message.append("Has extra IDs not in `")
                                    .append(referenceFolder).append("`: ");
                        }
                        List<String> sortedExtra = new ArrayList<>(extraIds);
                        Collections.sort(sortedExtra);
                        message.append("`").append(formatIds(sortedExtra)).append("`");
                    }

                    // Try to find a location in the folder context
                    Location location = null;
                    if (!missingIds.isEmpty()) {
                        // Report at the document level of the folder that's missing ids
                        // We don't have a great location, so use the file location
                        location = Location.create(folderContext.file);
                    } else {
                        // Extra ids - report at the location of the extra id
                        String firstExtra = new ArrayList<>(extraIds).get(0);
                        location = folderLocations.get(firstExtra);
                        if (location == null) {
                            location = Location.create(folderContext.file);
                        }
                    }

                    // Also get a secondary location in the reference folder
                    Map<String, Location> refLocations = locMap.get(referenceFolder);
                    XmlContext refContext = contextMap.get(referenceFolder);
                    Location secondary = null;
                    if (refLocations != null && !missingIds.isEmpty()) {
                        String firstMissing = new ArrayList<>(missingIds).get(0);
                        secondary = refLocations.get(firstMissing);
                        if (secondary == null && refContext != null) {
                            secondary = Location.create(refContext.file);
                        }
                    }

                    if (secondary != null) {
                        secondary.setMessage("Defined here");
                        location.setSecondary(secondary);
                    }

                    folderContext.report(ISSUE, location, message.toString());
                }
            }
        }
    }

    private static String formatIds(@NonNull List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append("`, `");
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
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // Not used in this detector
    }
}