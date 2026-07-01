package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
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

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n" +
            "\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n" +
            "\n" +
            "There **are** cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole layout",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE)));

    /**
     * Map from layout name to a map of folder name -> set of ids defined in that folder's version
     * of the layout.
     */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderIds = new HashMap<>();

    /**
     * Map from layout name to a map of folder name -> XmlContext (for location reporting).
     */
    private final Map<String, Map<String, XmlContext>> mLayoutToFolderContexts = new HashMap<>();

    /**
     * Set of layout names referenced via findViewById in Java/Kotlin code.
     */
    private final Set<String> mReferencedLayouts = new HashSet<>();

    public LayoutConsistencyDetector() {
    }

    // ---- XmlScanner / LayoutDetector ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String layoutName = getLayoutName(context.file);
        String folderName = context.file.getParentFile().getName();

        Set<String> ids = new LinkedHashSet<>();
        collectIds(document.getDocumentElement(), ids);

        Map<String, Set<String>> folderToIds =
                mLayoutToFolderIds.computeIfAbsent(layoutName, k -> new LinkedHashMap<>());
        folderToIds.put(folderName, ids);

        Map<String, XmlContext> folderToContext =
                mLayoutToFolderContexts.computeIfAbsent(layoutName, k -> new LinkedHashMap<>());
        folderToContext.put(folderName, context);
    }

    private static void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) {
            return;
        }
        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null) {
            Node idAttr = attrs.getNamedItemNS(ANDROID_URI, ATTR_ID);
            if (idAttr != null) {
                String value = idAttr.getNodeValue();
                if (value != null && !value.isEmpty()) {
                    // Normalize: strip @+id/ and @id/ prefixes
                    String id = normalizeId(value);
                    if (id != null && !id.isEmpty()) {
                        ids.add(id);
                    }
                }
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

    @Nullable
    private static String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    // ---- SourceCodeScanner ----

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
        if (type == com.android.tools.lint.detector.api.ResourceType.LAYOUT && !isFramework) {
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
        // Finding layouts referenced by code that calls findViewById is handled
        // via visitResourceReference above; this hook is kept for completeness.
    }

    // ---- After check ----

    @Override
    public void afterCheckRootProject(@NonNull com.android.tools.lint.detector.api.Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            if (folderToIds.size() < 2) {
                // Only one folder version; nothing to compare.
                continue;
            }

            // Compute the union of all ids across all folders.
            Set<String> allIds = new LinkedHashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            if (allIds.isEmpty()) {
                continue;
            }

            // For each folder, find ids that are missing compared to the union.
            Map<String, XmlContext> folderToContext = mLayoutToFolderContexts.get(layoutName);
            if (folderToContext == null) {
                continue;
            }

            // Build a map: folder -> missing ids (ids present in other folders but not this one)
            Map<String, Set<String>> folderToMissing = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> folderEntry : folderToIds.entrySet()) {
                String folder = folderEntry.getKey();
                Set<String> idsInFolder = folderEntry.getValue();
                Set<String> missing = new LinkedHashSet<>(allIds);
                missing.removeAll(idsInFolder);
                if (!missing.isEmpty()) {
                    folderToMissing.put(folder, missing);
                }
            }

            if (folderToMissing.isEmpty()) {
                continue;
            }

            // Report issues. We report on each folder that is missing ids.
            for (Map.Entry<String, Set<String>> missingEntry : folderToMissing.entrySet()) {
                String folder = missingEntry.getKey();
                Set<String> missingIds = missingEntry.getValue();

                XmlContext xmlContext = folderToContext.get(folder);
                if (xmlContext == null) {
                    continue;
                }

                // Build a secondary location chain for the other folders that DO have these ids.
                Location location = null;
                for (Map.Entry<String, Set<String>> otherEntry : folderToIds.entrySet()) {
                    String otherFolder = otherEntry.getKey();
                    if (otherFolder.equals(folder)) {
                        continue;
                    }
                    Set<String> otherIds = otherEntry.getValue();
                    // Check if the other folder has any of the missing ids.
                    boolean hasAny = false;
                    for (String id : missingIds) {
                        if (otherIds.contains(id)) {
                            hasAny = true;
                            break;
                        }
                    }
                    if (hasAny) {
                        XmlContext otherContext = folderToContext.get(otherFolder);
                        if (otherContext != null) {
                            Location otherLocation = Location.create(otherContext.file);
                            otherLocation.setMessage("Defined here");
                            if (location == null) {
                                location = otherLocation;
                            } else {
                                // Chain locations
                                Location tail = location;
                                while (tail.getSecondary() != null) {
                                    tail = tail.getSecondary();
                                }
                                tail.setSecondary(otherLocation);
                            }
                        }
                    }
                }

                Location primaryLocation = Location.create(xmlContext.file);
                if (location != null) {
                    primaryLocation.setSecondary(location);
                }

                List<String> sortedMissing = new ArrayList<>(missingIds);
                Collections.sort(sortedMissing);

                String message = String.format(
                        "The id `%1$s` in layout `%2$s` is missing from the following layout "
                                + "configurations: `%3$s`",
                        formatIds(sortedMissing),
                        layoutName,
                        folder);

                xmlContext.report(ISSUE, primaryLocation, message);
            }
        }
    }

    @NonNull
    private static String formatIds(@NonNull List<String> ids) {
        if (ids.size() == 1) {
            return ids.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}