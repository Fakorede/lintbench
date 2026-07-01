package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
                    "This check ensures that a layout resource which is defined in multiple resource folders, specifies the same set of widgets.\n\n" +
                    "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result in a runtime crash for some resource configurations when a `findViewById()` fails.\n\n" +
                    "There **are** cases where this is intentional. For example, you may have a dedicated large tablet layout which adds some extra widgets that are not present in the phone version of the layout. " +
                    "As long as the code accessing the layout resource is careful to handle this properly, it is valid. In that case, you can suppress this lint check for the given extra or missing views, or the whole layout",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Map<String, Set<String>>> layoutVariants = new HashMap<>();
    private final Map<String, Map<String, Location>> fileLocations = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        int dotIndex = fileName.indexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String variantKey = context.file.getPath();

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        layoutVariants.computeIfAbsent(baseName, k -> new HashMap<>()).put(variantKey, ids);
        fileLocations.computeIfAbsent(baseName, k -> new HashMap<>()).put(variantKey, context.getLocation(document.getDocumentElement()));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : layoutVariants.entrySet()) {
            String baseName = entry.getKey();
            Map<String, Set<String>> variants = entry.getValue();
            if (variants.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : variants.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<String, Set<String>> variantEntry : variants.entrySet()) {
                String variantPath = variantEntry.getKey();
                Set<String> variantIds = variantEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(variantIds);

                if (!missing.isEmpty()) {
                    Location location = fileLocations.get(baseName).get(variantPath);
                    List<String> sortedMissing = new ArrayList<>(missing);
                    Collections.sort(sortedMissing);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < sortedMissing.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append("@+id/").append(sortedMissing.get(i));
                    }
                    String message = String.format(
                            "This layout is missing the ID(s) %s which are present in other configurations of `%s`",
                            sb.toString(), baseName);
                    context.report(ISSUE, location, message);
                }
            }
        }
        layoutVariants.clear();
        fileLocations.clear();
    }

    private void collectIds(Element element, Set<String> ids) {
        if (element == null) return;
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && id.startsWith("@+id/")) {
            ids.add(id.substring(5));
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
        return false;
    }

    public void visitResourceReference(@NonNull Context context, @NonNull Object reference) {
        // Not applicable for this layout-only detector
    }
}