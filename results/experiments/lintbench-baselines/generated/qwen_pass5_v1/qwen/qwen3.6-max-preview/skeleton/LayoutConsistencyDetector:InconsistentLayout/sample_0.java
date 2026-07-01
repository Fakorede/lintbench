package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource folders, " +
                    "specifies the same set of widgets.\n\n" +
                    "This finds cases where you have accidentally forgotten to add a widget to all variations " +
                    "of the layout, which could result in a runtime crash for some resource configurations when " +
                    "a `findViewById()` fails.\n\n" +
                    "There **are** cases where this is intentional. For example, you may have a dedicated large " +
                    "tablet layout which adds some extra widgets that are not present in the phone version of the " +
                    "layout. As long as the code accessing the layout resource is careful to handle this properly, " +
                    "it is valid. In that case, you can suppress this lint check for the given extra or missing views, " +
                    "or the whole layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, LayoutData>> layoutMap = new HashMap<>();

    private static class LayoutData {
        final File file;
        final Set<String> ids;

        LayoutData(File file, Set<String> ids) {
            this.file = file;
            this.ids = ids;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }

        String layoutName = fileName.substring(0, fileName.length() - 4);
        File parent = context.file.getParentFile();
        String qualifier = parent != null ? parent.getName() : "";

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        layoutMap.computeIfAbsent(layoutName, k -> new HashMap<>())
                 .put(qualifier, new LayoutData(context.file, ids));
    }

    private void collectIds(Element element, Set<String> ids) {
        if (element == null) return;

        String id = element.getAttribute("android:id");
        if (id != null && !id.isEmpty()) {
            if (id.startsWith("@+id/")) {
                ids.add(id.substring(5));
            } else if (id.startsWith("@id/")) {
                ids.add(id.substring(4));
            } else {
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
        for (Map.Entry<String, Map<String, LayoutData>> entry : layoutMap.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, LayoutData> variations = entry.getValue();

            if (variations.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutData data : variations.values()) {
                allIds.addAll(data.ids);
            }

            for (Map.Entry<String, LayoutData> varEntry : variations.entrySet()) {
                String qualifier = varEntry.getKey();
                LayoutData data = varEntry.getValue();

                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(data.ids);

                if (!missing.isEmpty()) {
                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);
                    String msg = String.format(
                            "The layout \"%s\" in %s is missing the following ids: %s",
                            layoutName, qualifier, String.join(", ", sortedMissing));
                    context.report(ISSUE, Location.create(data.file), msg);
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    public void visitResourceReference() {
        // Not applicable for this layout consistency check
    }
}