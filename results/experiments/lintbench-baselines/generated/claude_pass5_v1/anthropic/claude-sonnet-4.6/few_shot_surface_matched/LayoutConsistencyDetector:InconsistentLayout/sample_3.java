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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String FIND_VIEW_BY_ID = "findViewById";

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
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)));

    /**
     * Map from layout base name to a map of folder qualifier -> set of ids defined in that layout.
     * e.g. "activity_main" -> { "layout" -> {"id1","id2"}, "layout-land" -> {"id1"} }
     */
    private final Map<String, Map<String, Set<String>>> mLayoutIds = new HashMap<>();

    /**
     * Map from layout base name to a map of folder qualifier -> the XmlContext location for that
     * layout file (used to report issues).
     */
    private final Map<String, Map<String, Location>> mLayoutLocations = new HashMap<>();

    /**
     * Set of layout names that are actually referenced via findViewById in Java/Kotlin code.
     */
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Get the layout file name without extension
        File file = context.file;
        String layoutName = file.getName();
        int dot = layoutName.lastIndexOf('.');
        if (dot != -1) {
            layoutName = layoutName.substring(0, dot);
        }

        // Get the folder qualifier (e.g., "layout", "layout-land", "layout-large")
        String folderName = file.getParentFile().getName();

        // Collect all IDs in this layout
        Set<String> ids = new LinkedHashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store the ids for this layout variant
        Map<String, Set<String>> variantMap = mLayoutIds.get(layoutName);
        if (variantMap == null) {
            variantMap = new LinkedHashMap<>();
            mLayoutIds.put(layoutName, variantMap);
        }
        variantMap.put(folderName, ids);

        // Store a location for this layout file
        Map<String, Location> locationMap = mLayoutLocations.get(layoutName);
        if (locationMap == null) {
            locationMap = new LinkedHashMap<>();
            mLayoutLocations.put(layoutName, locationMap);
        }
        locationMap.put(folderName, context.getLocation(document.getDocumentElement()));
    }

    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }

        // Check for android:id attribute
        String id = element.getAttributeNS(ANDROID_URI, "id");
        if (id != null && !id.isEmpty()) {
            // Normalize: strip @+id/ or @id/ prefix
            ids.add(normalizeId(id));
        }

        // Recurse into children
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    private String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull com.android.tools.lint.detector.api.ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        // Track which layout resources are referenced
        if (type == com.android.tools.lint.detector.api.ResourceType.ID) {
            // We track IDs that are looked up - not directly layouts
            // The layout name tracking is done via the XML scanning
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(FIND_VIEW_BY_ID);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        // When we see a findViewById call, we know the containing class uses layouts
        // We don't need to track specific layouts here since we analyze all layout files
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now compare layout variants and report inconsistencies
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> variantMap = entry.getValue();

            // Only care about layouts defined in multiple folders
            if (variantMap.size() < 2) {
                continue;
            }

            // Find the union of all IDs across all variants
            Set<String> allIds = new LinkedHashSet<>();
            for (Set<String> ids : variantMap.values()) {
                allIds.addAll(ids);
            }

            // For each variant, check if it's missing any IDs
            Map<String, Location> locationMap = mLayoutLocations.get(layoutName);

            // Build a map of id -> which folders define it
            Map<String, List<String>> idToFolders = new LinkedHashMap<>();
            for (String id : allIds) {
                List<String> foldersWithId = new ArrayList<>();
                for (Map.Entry<String, Set<String>> variantEntry : variantMap.entrySet()) {
                    if (variantEntry.getValue().contains(id)) {
                        foldersWithId.add(variantEntry.getKey());
                    }
                }
                idToFolders.put(id, foldersWithId);
            }

            // Find IDs that are not in all variants
            List<String> folders = new ArrayList<>(variantMap.keySet());
            int folderCount = folders.size();

            // Collect missing IDs per folder
            Map<String, List<String>> folderToMissingIds = new LinkedHashMap<>();
            for (String folder : folders) {
                Set<String> idsInFolder = variantMap.get(folder);
                List<String> missingIds = new ArrayList<>();
                for (String id : allIds) {
                    if (!idsInFolder.contains(id)) {
                        missingIds.add(id);
                    }
                }
                if (!missingIds.isEmpty()) {
                    folderToMissingIds.put(folder, missingIds);
                }
            }

            if (folderToMissingIds.isEmpty()) {
                continue;
            }

            // Report the issue for each folder that has missing IDs
            for (Map.Entry<String, List<String>> missingEntry : folderToMissingIds.entrySet()) {
                String folder = missingEntry.getKey();
                List<String> missingIds = missingEntry.getValue();

                Location location = null;
                if (locationMap != null) {
                    location = locationMap.get(folder);
                }

                // Build secondary locations for other folders
                Location secondary = null;
                if (locationMap != null) {
                    for (Map.Entry<String, Location> locEntry : locationMap.entrySet()) {
                        if (!locEntry.getKey().equals(folder)) {
                            Location loc = locEntry.getValue();
                            loc.setMessage("Defined here");
                            if (secondary == null) {
                                secondary = loc;
                            }
                        }
                    }
                }

                if (location == null) {
                    continue;
                }

                if (secondary != null) {
                    location.setSecondary(secondary);
                }

                // Find which folders DO have these ids (for a better message)
                StringBuilder sb = new StringBuilder();
                sb.append("The id `");
                if (missingIds.size() == 1) {
                    sb.append(missingIds.get(0));
                    sb.append("` in layout `");
                    sb.append(layoutName);
                    sb.append("` is missing from the `");
                    sb.append(folder);
                    sb.append("` layout");
                } else {
                    sb.append("` and other ids in layout `");
                    sb.append(layoutName);
                    sb.append("` are missing from the `");
                    sb.append(folder);
                    sb.append("` layout");
                    sb = new StringBuilder();
                    sb.append("The ids ");
                    for (int i = 0; i < missingIds.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append("`").append(missingIds.get(i)).append("`");
                    }
                    sb.append(" in layout `");
                    sb.append(layoutName);
                    sb.append("` are missing from the `");
                    sb.append(folder);
                    sb.append("` layout");
                }

                // Find which folders have the missing ids
                Set<String> foldersWithIds = new LinkedHashSet<>();
                for (String missingId : missingIds) {
                    List<String> foldersForId = idToFolders.get(missingId);
                    if (foldersForId != null) {
                        foldersWithIds.addAll(foldersForId);
                    }
                }
                foldersWithIds.remove(folder);

                if (!foldersWithIds.isEmpty()) {
                    sb.append(" (present in ");
                    List<String> present = new ArrayList<>(foldersWithIds);
                    for (int i = 0; i < present.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append("`").append(present.get(i)).append("`");
                    }
                    sb.append(")");
                }

                context.report(ISSUE, location, sb.toString());
            }
        }
    }
}