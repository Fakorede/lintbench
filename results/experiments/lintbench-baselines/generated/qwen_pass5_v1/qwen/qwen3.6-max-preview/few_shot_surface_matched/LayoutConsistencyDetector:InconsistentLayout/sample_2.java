package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import org.jetbrains.uast.UReference;
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
            "This check ensures that a layout resource which is defined in multiple resource folders specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result in a runtime crash for some resource configurations when a `findViewById()` fails.\n\n" +
            "There **are** cases where this is intentional. For example, you may have a dedicated large tablet layout which adds some extra widgets that are not present in the phone version of the layout. As long as the code accessing the layout resource is careful to handle this properly, it is valid. In that case, you can suppress this lint check for the given extra or missing views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static class VariationInfo {
        final File file;
        final Set<String> ids;

        VariationInfo(File file, Set<String> ids) {
            this.file = file;
            this.ids = ids;
        }
    }

    private final Map<String, Map<String, VariationInfo>> layoutVariations = new HashMap<>();
    private final Set<String> referencedIds = new HashSet<>();

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

        String qualifier = "default";
        if (context.getConfiguration() != null) {
            String q = context.getConfiguration().getQualifierString();
            if (q != null && !q.isEmpty()) {
                qualifier = q;
            }
        }

        Set<String> ids = new HashSet<>();
        Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(root, ids);
        }

        layoutVariations.computeIfAbsent(layoutName, k -> new HashMap<>())
                .put(qualifier, new VariationInfo(context.file, ids));
    }

    private void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        String id = element.getAttribute("android:id");
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
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull UReference reference, @NonNull ResourceType type, @NonNull String name, boolean isFramework) {
        if (type == ResourceType.ID) {
            referencedIds.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, VariationInfo>> entry : layoutVariations.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, VariationInfo> variations = entry.getValue();
            if (variations.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (VariationInfo info : variations.values()) {
                allIds.addAll(info.ids);
            }

            for (Map.Entry<String, VariationInfo> varEntry : variations.entrySet()) {
                String qualifier = varEntry.getKey();
                VariationInfo info = varEntry.getValue();
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(info.ids);

                if (!referencedIds.isEmpty()) {
                    missing.retainAll(referencedIds);
                }

                if (!missing.isEmpty()) {
                    String missingList = String.join(", ", missing);
                    String message = String.format(
                            "The layout \"%1$s\" in configuration \"%2$s\" is missing the following IDs that are present in other configurations: %3$s",
                            layoutName, qualifier, missingList);
                    context.report(ISSUE, Location.create(info.file), message);
                }
            }
        }
    }
}