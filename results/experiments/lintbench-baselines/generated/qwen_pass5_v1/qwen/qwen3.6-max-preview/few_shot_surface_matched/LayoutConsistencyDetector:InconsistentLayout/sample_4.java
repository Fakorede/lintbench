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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UReference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result in a runtime crash for some resource configurations when a findViewById() fails.\n\n" +
            "There **are** cases where this is intentional. For example, you may have a dedicated large tablet layout which adds some extra widgets that are not present in the phone version of the layout. As long as the code accessing the layout resource is careful to handle this properly, it is valid. In that case, you can suppress this lint check for the given extra or missing views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    private static class Variation {
        final File file;
        final Set<String> ids;

        Variation(File file, Set<String> ids) {
            this.file = file;
            this.ids = ids;
        }
    }

    private final Map<String, Map<String, Variation>> layouts = new HashMap<>();
    private final Set<String> referencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String fileName = context.file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String layoutName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String folderName = context.file.getParentFile() != null ? context.file.getParentFile().getName() : "";

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        layouts.computeIfAbsent(layoutName, k -> new HashMap<>())
               .put(folderName, new Variation(context.file, ids));
    }

    private void collectIds(@Nullable Element element, @NonNull Set<String> ids) {
        if (element == null) return;
        if (element.hasAttribute("android:id")) {
            String id = element.getAttribute("android:id");
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
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Variation>> entry : layouts.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Variation> variations = entry.getValue();
            if (variations.size() < 2) continue;

            if (!referencedLayouts.isEmpty() && !referencedLayouts.contains(layoutName)) {
                continue;
            }

            List<Map.Entry<String, Variation>> list = new ArrayList<>(variations.entrySet());
            list.sort(Comparator.comparing(Map.Entry::getKey));

            Variation baseline = list.get(0).getValue();
            String baselineFolder = list.get(0).getKey();

            for (int i = 1; i < list.size(); i++) {
                Map.Entry<String, Variation> varEntry = list.get(i);
                Variation current = varEntry.getValue();
                String currentFolder = varEntry.getKey();

                Set<String> missing = new HashSet<>(baseline.ids);
                missing.removeAll(current.ids);

                if (!missing.isEmpty()) {
                    String msg = String.format(
                        "The layout \"%1$s\" in %2$s is missing the following widgets present in %3$s: %4$s",
                        layoutName, currentFolder, baselineFolder, missing);
                    context.report(ISSUE, Location.create(current.file), msg);
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull UReference reference,
                                       @NonNull String type, @NonNull String name,
                                       boolean isFramework, @Nullable Object cookie) {
        if ("layout".equals(type)) {
            referencedLayouts.add(name);
        }
    }
}