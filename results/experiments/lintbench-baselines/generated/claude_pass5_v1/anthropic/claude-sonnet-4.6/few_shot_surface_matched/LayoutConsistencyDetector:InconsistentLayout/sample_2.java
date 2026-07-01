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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.ID_PREFIX;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

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
                            + "layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            Scope.ALL_RESOURCES_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    /** Map from layout name to a map of folder -> set of ids defined in that folder's version */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderToIds = new HashMap<>();

    /** Map from layout name to a map of folder -> XmlContext for reporting */
    private final Map<String, Map<String, XmlContext>> mLayoutToFolderToContext = new HashMap<>();

    /** Set of layout ids referenced via findViewById in Java/Kotlin code */
    private final Set<String> mReferencedIds = new HashSet<>();

    /** Map from layout name to list of locations for each folder variant */
    private final Map<String, Map<String, Location>> mLayoutToFolderToLocation = new HashMap<>();

    public LayoutConsistencyDetector() {}

    // -----------------------------------------------------------------------
    // LayoutDetector / XmlScanner
    // -----------------------------------------------------------------------

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String layoutName = getLayoutName(file);
        if (layoutName == null) {
            return;
        }

        // The qualifier folder name (e.g. "layout", "layout-land", "layout-large")
        String folderName = file.getParentFile().getName();

        // Collect all ids defined in this layout file
        Set<String> ids = new LinkedHashSet<>();
        collectIds(document.getDocumentElement(), ids);

        // Store results
        Map<String, Set<String>> folderToIds =
                mLayoutToFolderToIds.computeIfAbsent(layoutName, k -> new LinkedHashMap<>());
        folderToIds.put(folderName, ids);

        Map<String, XmlContext> folderToContext =
                mLayoutToFolderToContext.computeIfAbsent(layoutName, k -> new LinkedHashMap<>());
        folderToContext.put(folderName, context);

        Map<String, Location> folderToLocation =
                mLayoutToFolderToLocation.computeIfAbsent(layoutName, k -> new LinkedHashMap<>());
        folderToLocation.put(folderName, context.getLocation(document.getDocumentElement()));
    }

    private static void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            Node idAttr = attributes.getNamedItemNS(
                    "http://schemas.android.com/apk/res/android", "id");
            if (idAttr == null) {
                // Try without namespace
                idAttr = attributes.getNamedItem(ATTR_ID);
            }
            if (idAttr != null) {
                String value = idAttr.getNodeValue();
                if (value != null) {
                    String id = stripIdPrefix(value);
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    @NonNull
    private static String stripIdPrefix(@NonNull String id) {
        if (id.startsWith(NEW_ID_PREFIX)) {
            return id.substring(NEW_ID_PREFIX.length());
        } else if (id.startsWith(ID_PREFIX)) {
            return id.substring(ID_PREFIX.length());
        }
        return id;
    }

    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner — track findViewById calls
    // -----------------------------------------------------------------------

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
        if (!isFramework && type == ResourceType.ID) {
            mReferencedIds.add(name);
        }
    }

    // -----------------------------------------------------------------------
    // afterCheckRootProject — report inconsistencies
    // -----------------------------------------------------------------------

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderToIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            if (folderToIds.size() < 2) {
                // Only one variant — nothing to compare
                continue;
            }

            // Compute the union of all ids across all folders
            Set<String> allIds = new LinkedHashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            if (allIds.isEmpty()) {
                continue;
            }

            // For each folder, find ids that are missing compared to other folders
            Map<String, XmlContext> folderToContext = mLayoutToFolderToContext.get(layoutName);
            Map<String, Location> folderToLocation = mLayoutToFolderToLocation.get(layoutName);

            // Build a map: id -> list of folders that define it
            Map<String, List<String>> idToFolders = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> fe : folderToIds.entrySet()) {
                String folder = fe.getKey();
                for (String id : fe.getValue()) {
                    idToFolders.computeIfAbsent(id, k -> new ArrayList<>()).add(folder);
                }
            }

            // Collect ids that are not present in ALL folders (inconsistent)
            Set<String> inconsistentIds = new LinkedHashSet<>();
            for (Map.Entry<String, List<String>> ie : idToFolders.entrySet()) {
                if (ie.getValue().size() < folderToIds.size()) {
                    inconsistentIds.add(ie.getKey());
                }
            }

            if (inconsistentIds.isEmpty()) {
                continue;
            }

            // Only report ids that are actually referenced in code
            Set<String> reportableIds = new LinkedHashSet<>();
            for (String id : inconsistentIds) {
                if (mReferencedIds.contains(id)) {
                    reportableIds.add(id);
                }
            }

            // If we have no referenced ids, still report all inconsistent ids
            // (the spec says to find cases where widget is missing)
            if (reportableIds.isEmpty()) {
                reportableIds = inconsistentIds;
            }

            // Report for each folder that is missing some ids
            for (Map.Entry<String, Set<String>> fe : folderToIds.entrySet()) {
                String folder = fe.getKey();
                Set<String> idsInFolder = fe.getValue();

                Set<String> missingInThisFolder = new LinkedHashSet<>();
                for (String id : reportableIds) {
                    if (!idsInFolder.contains(id)) {
                        missingInThisFolder.add(id);
                    }
                }

                if (missingInThisFolder.isEmpty()) {
                    continue;
                }

                XmlContext xmlContext = folderToContext != null ? folderToContext.get(folder) : null;
                Location location = folderToLocation != null ? folderToLocation.get(folder) : null;

                // Build secondary locations pointing to folders that DO have the missing ids
                Location secondary = null;
                for (String missingId : missingInThisFolder) {
                    List<String> foldersWithId = idToFolders.get(missingId);
                    if (foldersWithId != null) {
                        for (String otherFolder : foldersWithId) {
                            if (!otherFolder.equals(folder)) {
                                Location otherLocation =
                                        folderToLocation != null
                                                ? folderToLocation.get(otherFolder)
                                                : null;
                                if (otherLocation != null) {
                                    Location loc = otherLocation.withMessage(
                                            "Defined here but not in `" + folder + "`");
                                    if (secondary == null) {
                                        secondary = loc;
                                    } else {
                                        // Chain secondary locations
                                        Location last = secondary;
                                        while (last.getSecondary() != null) {
                                            last = last.getSecondary();
                                        }
                                        last.setSecondary(loc);
                                    }
                                }
                            }
                        }
                    }
                }

                if (location != null && secondary != null) {
                    location.setSecondary(secondary);
                }

                String message = buildMessage(layoutName, folder, missingInThisFolder, folderToIds);

                if (xmlContext != null && location != null) {
                    xmlContext.report(ISSUE, location, message);
                } else if (location != null) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    @NonNull
    private static String buildMessage(
            @NonNull String layoutName,
            @NonNull String folder,
            @NonNull Set<String> missingIds,
            @NonNull Map<String, Set<String>> folderToIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("The `").append(layoutName).append("` layout in `").append(folder)
                .append("` is missing the following IDs that exist in other configurations: ");

        boolean first = true;
        for (String id : missingIds) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("`").append(id).append("`");
            first = false;
        }

        // List which other folders define these ids
        Set<String> otherFolders = new LinkedHashSet<>(folderToIds.keySet());
        otherFolders.remove(folder);
        if (!otherFolders.isEmpty()) {
            sb.append(" (defined in: ");
            boolean firstFolder = true;
            for (String other : otherFolders) {
                if (!firstFolder) {
                    sb.append(", ");
                }
                sb.append("`").append(other).append("`");
                firstFolder = false;
            }
            sb.append(")");
        }

        return sb.toString();
    }
}