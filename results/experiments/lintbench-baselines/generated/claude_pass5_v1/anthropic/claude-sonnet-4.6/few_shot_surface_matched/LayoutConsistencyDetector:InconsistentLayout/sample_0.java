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
import java.io.File;
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

    // Map from layout name to a map of folder -> set of ids defined in that folder's version
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderIds = new HashMap<>();

    // Map from layout name to a map of folder -> location of the layout file
    private final Map<String, Map<String, Location>> mLayoutToFolderLocation = new HashMap<>();

    // Set of layout names referenced via findViewById in Java/Kotlin code
    private final Set<String> mReferencedLayouts = new HashSet<>();

    private static final String ATTR_ID = "android:id";
    private static final String FIND_VIEW_BY_ID = "findViewById";
    private static final String ID_PREFIX = "@+id/";
    private static final String ID_REF_PREFIX = "@id/";

    public LayoutConsistencyDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String layoutName = getLayoutName(context.file);
        if (layoutName == null) {
            return;
        }

        String folderName = context.file.getParentFile().getName();

        Set<String> ids = collectIds(document.getDocumentElement());

        Map<String, Set<String>> folderIds =
                mLayoutToFolderIds.computeIfAbsent(layoutName, k -> new HashMap<>());
        folderIds.put(folderName, ids);

        Map<String, Location> folderLocations =
                mLayoutToFolderLocation.computeIfAbsent(layoutName, k -> new HashMap<>());
        folderLocations.put(folderName, context.getLocation(document.getDocumentElement()));
    }

    private Set<String> collectIds(@Nullable Element element) {
        Set<String> ids = new HashSet<>();
        if (element == null) {
            return ids;
        }
        collectIdsFromElement(element, ids);
        return ids;
    }

    private void collectIdsFromElement(@NonNull Element element, @NonNull Set<String> ids) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            Node idAttr = attributes.getNamedItem(ATTR_ID);
            if (idAttr != null) {
                String idValue = idAttr.getNodeValue();
                if (idValue != null) {
                    String id = stripIdPrefix(idValue);
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIdsFromElement((Element) child, ids);
            }
        }
    }

    private String stripIdPrefix(@NonNull String id) {
        if (id.startsWith(ID_PREFIX)) {
            return id.substring(ID_PREFIX.length());
        } else if (id.startsWith(ID_REF_PREFIX)) {
            return id.substring(ID_REF_PREFIX.length());
        }
        return id;
    }

    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name.isEmpty() ? null : name;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderIds = entry.getValue();

            if (folderIds.size() < 2) {
                // Only one folder variant — nothing to compare
                continue;
            }

            // Compute union of all ids across all folder variants
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderIds.values()) {
                allIds.addAll(ids);
            }

            // Check each folder variant against the union
            for (Map.Entry<String, Set<String>> folderEntry : folderIds.entrySet()) {
                String folder = folderEntry.getKey();
                Set<String> ids = folderEntry.getValue();

                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    Map<String, Location> locations = mLayoutToFolderLocation.get(layoutName);
                    Location location = locations != null ? locations.get(folder) : null;

                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);

                    StringBuilder sb = new StringBuilder();
                    sb.append("The `").append(layoutName).append("` layout in folder `")
                            .append(folder).append("` is missing the following IDs that are "
                                    + "present in other layout folders: ");
                    for (int i = 0; i < sortedMissing.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append("`").append(sortedMissing.get(i)).append("`");
                    }

                    if (location != null) {
                        context.report(ISSUE, location, sb.toString());
                    }
                }
            }
        }
    }

    // SourceCodeScanner methods

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
        // We track findViewById calls to identify which layouts are being accessed.
        // The actual consistency check is done in afterCheckRootProject.
    }
}