package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    "tablet layout which adds some extra widgets that are not present in the phone version of " +
                    "the layout. As long as the code accessing the layout resource is careful to handle this " +
                    "properly, it is valid. In that case, you can suppress this lint check for the given extra " +
                    "or missing views, or the whole layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, Set<String>>> layoutIds = new HashMap<>();
    private final Map<String, Map<String, XmlContext>> layoutContexts = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        File parent = file.getParentFile();
        String folderName = parent != null ? parent.getName() : "layout";
        String qualifier = folderName.contains("-") ? folderName.substring(folderName.indexOf('-') + 1) : "";

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        layoutIds.computeIfAbsent(baseName, k -> new HashMap<>()).put(qualifier, ids);
        layoutContexts.computeIfAbsent(baseName, k -> new HashMap<>()).put(qualifier, context);
    }

    private void collectIds(Element element, Set<String> ids) {
        if (element == null) return;
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            int slash = id.indexOf('/');
            if (slash != -1 && slash + 1 < id.length()) {
                ids.add(id.substring(slash + 1));
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
        for (Map.Entry<String, Map<String, Set<String>>> entry : layoutIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> configs = entry.getValue();
            if (configs.size() < 2) continue;

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : configs.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<String, Set<String>> configEntry : configs.entrySet()) {
                String qualifier = configEntry.getKey();
                Set<String> currentIds = configEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(currentIds);
                if (!missing.isEmpty()) {
                    XmlContext ctx = layoutContexts.get(layoutName).get(qualifier);
                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);
                    String missingStr = String.join(", ", sortedMissing);
                    String configLabel = qualifier.isEmpty() ? "default" : qualifier;
                    String message = String.format(
                        "The layout `%s` in configuration `%s` is missing the following IDs: `%s` which are present in other configurations",
                        layoutName, configLabel, missingStr);
                    ctx.report(ISSUE, ctx.getLocation(ctx.getDocument().getDocumentElement()), message);
                }
            }
        }
        layoutIds.clear();
        layoutContexts.clear();
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    public void visitResourceReference(@NonNull XmlContext context, @NonNull Object url, @NonNull Element element, @NonNull String name, @NonNull String type, boolean isFramework) {
        // Not used for this detector
    }
}