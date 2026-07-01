package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders specifies the same set of widgets. " +
            "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a findViewById() fails. There **are** cases where this is intentional. " +
            "For example, you may have a dedicated large tablet layout which adds some extra widgets that are not present in the phone " +
            "version of the layout. As long as the code accessing the layout resource is careful to handle this properly, it is valid. " +
            "In that case, you can suppress this lint check for the given extra or missing views, or the whole layout.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Map<String, Set<String>>> layouts = new HashMap<>();
    private final Map<String, Map<String, File>> layoutFiles = new HashMap<>();
    private final Set<String> referencedIds = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return file.getName().endsWith(".xml");
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String layoutName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        String folderName = context.file.getParentFile().getName();
        String config = folderName.startsWith("layout-") ? folderName.substring(7) : "";

        Set<String> ids = layouts.computeIfAbsent(layoutName, k -> new HashMap<>())
                .computeIfAbsent(config, k -> new HashSet<>());
        layoutFiles.computeIfAbsent(layoutName, k -> new HashMap<>()).put(config, context.file);

        collectIds(document.getDocumentElement(), ids);
    }

    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) return;
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            if (id.startsWith("@+id/")) {
                ids.add(id.substring(5));
            } else if (id.startsWith("@id/")) {
                ids.add(id.substring(4));
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
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @Nullable ResourceType type, @Nullable String name, boolean isFramework) {
        if (type == ResourceType.ID && name != null) {
            referencedIds.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : layouts.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> configs = entry.getValue();
            if (configs.size() < 2) continue;

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : configs.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<String, Set<String>> configEntry : configs.entrySet()) {
                String config = configEntry.getKey();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(configEntry.getValue());

                for (String id : missing) {
                    if (!referencedIds.contains(id)) continue;

                    File file = layoutFiles.get(layoutName).get(config);
                    Location location = Location.create(file);
                    String configDesc = config.isEmpty() ? "default" : config;
                    String message = String.format(
                            "The layout `%s` in configuration `%s` is missing the ID `%s` which is present in other configurations. " +
                            "This could result in a runtime crash if `findViewById()` is called.", layoutName, configDesc, id);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}