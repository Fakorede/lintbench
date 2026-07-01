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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
                            + "layout",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)));

    private static final String ATTR_ID = "android:id";
    private static final String ID_PREFIX = "@+id/";
    private static final String ID_PREFIX_NO_PLUS = "@id/";
    private static final String FIND_VIEW_BY_ID = "findViewById";

    /**
     * Map from layout name to a map of folder configuration to set of ids defined in that folder's
     * version of the layout.
     */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderIds = new HashMap<>();

    /**
     * Map from layout name to the XmlContext of the first occurrence (used for reporting).
     */
    private final Map<String, XmlContext> mLayoutToContext = new HashMap<>();

    /**
     * Map from layout name to map of folder to Location for reporting.
     */
    private final Map<String, Map<String, Location>> mLayoutToLocations = new HashMap<>();

    /**
     * Set of layout names referenced via findViewById in Java/Kotlin code.
     */
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String folderName = context.file.getParentFile().getName();
        String layoutName = getLayoutName(context);

        Set<String> ids = collectIds(document.getDocumentElement());

        Map<String, Set<String>> folderMap =
                mLayoutToFolderIds.computeIfAbsent(layoutName, k -> new HashMap<>());
        folderMap.put(folderName, ids);

        if (!mLayoutToContext.containsKey(layoutName)) {
            mLayoutToContext.put(layoutName, context);
        }

        Map<String, Location> locationMap =
                mLayoutToLocations.computeIfAbsent(layoutName, k -> new HashMap<>());
        locationMap.put(folderName, context.getLocation(document.getDocumentElement()));
    }

    private static String getLayoutName(@NonNull XmlContext context) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }

    private static Set<String> collectIds(@Nullable Element element) {
        Set<String> ids = new HashSet<>();
        if (element == null) {
            return ids;
        }
        collectIdsRecursive(element, ids);
        return ids;
    }

    private static void collectIdsRecursive(@NonNull Element element, @NonNull Set<String> ids) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            Node idAttr = attributes.getNamedItem(ATTR_ID);
            if (idAttr != null) {
                String value = idAttr.getNodeValue();
                if (value != null) {
                    if (value.startsWith(ID_PREFIX)) {
                        ids.add(value.substring(ID_PREFIX.length()));
                    } else if (value.startsWith(ID_PREFIX_NO_PLUS)) {
                        ids.add(value.substring(ID_PREFIX_NO_PLUS.length()));
                    } else {
                        ids.add(value);
                    }
                }
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIdsRecursive((Element) child, ids);
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
            @NonNull UCallExpression node,
            @NonNull String type,
            @NonNull String name,
            boolean isFramework) {
        if (!isFramework && "layout".equals(type)) {
            mReferencedLayouts.add(name);
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
        // We handle this via visitResourceReference; this is kept for completeness.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderIds = entry.getValue();

            if (folderIds.size() < 2) {
                // Only one variant, nothing to compare.
                continue;
            }

            // Compute the union and intersection of all id sets across folders.
            Set<String> union = new HashSet<>();
            Set<String> intersection = null;

            for (Set<String> ids : folderIds.values()) {
                union.addAll(ids);
                if (intersection == null) {
                    intersection = new HashSet<>(ids);
                } else {
                    intersection.retainAll(ids);
                }
            }

            if (intersection == null) {
                intersection = new HashSet<>();
            }

            // If union equals intersection, all folders have the same ids.
            if (union.equals(intersection)) {
                continue;
            }

            // There are inconsistencies. Find which ids are missing from which folders.
            Map<String, Location> locationMap = mLayoutToLocations.get(layoutName);

            // Build a report: for each folder, find ids it is missing.
            List<String> folders = new ArrayList<>(folderIds.keySet());
            Collections.sort(folders);

            StringBuilder message = new StringBuilder();
            message.append("The id-referenced widgets in layout `")
                    .append(layoutName)
                    .append("` are not consistently defined across configurations:");

            boolean anyInconsistency = false;
            for (String folder : folders) {
                Set<String> idsInFolder = folderIds.get(folder);
                Set<String> missingIds = new HashSet<>(union);
                missingIds.removeAll(idsInFolder);

                if (!missingIds.isEmpty()) {
                    anyInconsistency = true;
                    List<String> sortedMissing = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissing);
                    message.append("\n* `").append(folder).append("` is missing: ");
                    for (int i = 0; i < sortedMissing.size(); i++) {
                        if (i > 0) {
                            message.append(", ");
                        }
                        message.append("`").append(sortedMissing.get(i)).append("`");
                    }
                }
            }

            if (!anyInconsistency) {
                continue;
            }

            // Find the primary location (first folder alphabetically).
            Location primaryLocation = null;
            Location secondaryLocation = null;

            if (locationMap != null) {
                for (String folder : folders) {
                    Location loc = locationMap.get(folder);
                    if (loc != null) {
                        if (primaryLocation == null) {
                            primaryLocation = loc;
                        } else if (secondaryLocation == null) {
                            secondaryLocation = loc;
                            break;
                        }
                    }
                }
            }

            if (primaryLocation == null) {
                // Fallback: use context from mLayoutToContext
                XmlContext xmlContext = mLayoutToContext.get(layoutName);
                if (xmlContext != null) {
                    primaryLocation = Location.create(xmlContext.file);
                }
            }

            if (primaryLocation != null) {
                if (secondaryLocation != null) {
                    primaryLocation = primaryLocation.withSecondary(
                            secondaryLocation,
                            "Also defined here");
                }
                context.report(ISSUE, primaryLocation, message.toString());
            }
        }
    }
}