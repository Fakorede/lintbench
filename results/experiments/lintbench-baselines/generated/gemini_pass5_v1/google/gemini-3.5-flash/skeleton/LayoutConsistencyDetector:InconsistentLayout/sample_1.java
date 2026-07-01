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
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets. This finds cases "
                            + "where you have accidentally forgotten to add a widget to all variations "
                            + "of the layout, which could result in a runtime crash for some resource "
                            + "configurations when a `findViewById()` fails.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<LayoutDefinition>> layoutDefinitions = new HashMap<>();

    private static class LayoutDefinition {
        final File file;
        final String folderName;
        final Set<String> ids;
        final Location location;

        LayoutDefinition(File file, String folderName, Set<String> ids, Location location) {
            this.file = file;
            this.folderName = folderName;
            this.ids = ids;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String fileName = file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);
        String folderName = file.getParentFile().getName();

        Set<String> ids = new HashSet<>();
        Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(root, ids);
        }

        Location location = context.getLocation(root != null ? root : document);

        synchronized (layoutDefinitions) {
            List<LayoutDefinition> list = layoutDefinitions.computeIfAbsent(layoutName, k -> new ArrayList<>());
            list.add(new LayoutDefinition(file, folderName, ids, location));
        }
    }

    private void collectIds(Element element, Set<String> ids) {
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            int index = id.indexOf('/');
            if (index != -1) {
                ids.add(id.substring(index + 1));
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
        for (Map.Entry<String, List<LayoutDefinition>> entry : layoutDefinitions.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutDefinition> definitions = entry.getValue();
            if (definitions.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutDefinition def : definitions) {
                allIds.addAll(def.ids);
            }

            for (LayoutDefinition def : definitions) {
                for (String id : allIds) {
                    if (!def.ids.contains(id)) {
                        StringBuilder definedIn = new StringBuilder();
                        for (LayoutDefinition other : definitions) {
                            if (other.ids.contains(id)) {
                                if (definedIn.length() > 0) {
                                    definedIn.append(", ");
                                }
                                definedIn.append(other.folderName);
                            }
                        }

                        String message = String.format(
                                "Layout `%s` in `%s` is missing ID `%s` (defined in %s)",
                                layoutName,
                                def.folderName,
                                id,
                                definedIn.toString());

                        context.report(ISSUE, def.location, message);
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }
}