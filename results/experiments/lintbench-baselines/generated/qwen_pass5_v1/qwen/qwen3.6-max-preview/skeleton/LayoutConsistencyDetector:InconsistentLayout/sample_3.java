package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.jetbrains.uast.UReference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource folders specifies the same set of widgets. " +
                    "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result " +
                    "in a runtime crash for some resource configurations when a findViewById() fails. " +
                    "There are cases where this is intentional. For example, you may have a dedicated large tablet layout which adds some extra " +
                    "widgets that are not present in the phone version of the layout. As long as the code accessing the layout resource is careful " +
                    "to handle this properly, it is valid. In that case, you can suppress this lint check for the given extra or missing views, or the whole layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static class LayoutConfig {
        final Location location;
        final String qualifier;
        final Set<String> ids;

        LayoutConfig(Location location, String qualifier, Set<String> ids) {
            this.location = location;
            this.qualifier = qualifier;
            this.ids = ids;
        }
    }

    private final Map<String, List<LayoutConfig>> layoutMap = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) return;

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot != -1 ? fileName.substring(0, dot) : fileName;

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        Location location = context.getLocation(root);
        String qualifier = context.file.getParentFile() != null ? context.file.getParentFile().getName() : "unknown";

        layoutMap.computeIfAbsent(baseName, k -> new ArrayList<>())
                .add(new LayoutConfig(location, qualifier, ids));
    }

    private void collectIds(Element element, Set<String> ids) {
        String id = element.getAttributeNS(ANDROID_URI, "id");
        if (id != null && !id.isEmpty()) {
            int slash = id.lastIndexOf('/');
            ids.add(slash != -1 ? id.substring(slash + 1) : id);
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
        for (Map.Entry<String, List<LayoutConfig>> entry : layoutMap.entrySet()) {
            List<LayoutConfig> configs = entry.getValue();
            if (configs.size() < 2) continue;

            Set<String> allIds = new HashSet<>();
            for (LayoutConfig config : configs) {
                allIds.addAll(config.ids);
            }

            for (LayoutConfig config : configs) {
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(config.ids);
                if (!missing.isEmpty()) {
                    String missingList = missing.stream()
                            .sorted()
                            .map(id -> "@id/" + id)
                            .collect(Collectors.joining(", "));
                    String message = String.format(
                            "The layout `%s` in folder `%s` is missing the following views: %s which are present in other configurations",
                            entry.getKey(), config.qualifier, missingList);
                    context.report(ISSUE, config.location, message);
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull UReference reference, @NonNull String name) {
        // Not applicable for this detector
    }
}