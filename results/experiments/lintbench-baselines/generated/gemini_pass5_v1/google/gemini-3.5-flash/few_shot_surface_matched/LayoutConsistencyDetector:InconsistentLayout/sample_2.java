package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets. This finds "
                            + "cases where you have accidentally forgotten to add a widget to all "
                            + "variations of the layout, which could result in a runtime crash "
                            + "for some resource configurations when a `findViewById()` fails.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

    private final Map<String, Map<String, Set<String>>> layoutToFolderToIds = new HashMap<>();
    private final Map<String, Map<String, Location>> layoutToFolderToLocation = new HashMap<>();
    private final Set<String> referencedLayouts = Collections.synchronizedSet(new HashSet<>());

    public LayoutConsistencyDetector() {}

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull Document document) {
        String layoutName = context.file.getName();
        int dot = layoutName.lastIndexOf('.');
        if (dot != -1) {
            layoutName = layoutName.substring(0, dot);
        }
        String folderName = context.file.getParentFile().getName();

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        synchronized (layoutToFolderToIds) {
            Map<String, Set<String>> folderToIds = layoutToFolderToIds.computeIfAbsent(layoutName, k -> new HashMap<>());
            folderToIds.put(folderName, ids);

            Map<String, Location> folderToLoc = layoutToFolderToLocation.computeIfAbsent(layoutName, k -> new HashMap<>());
            folderToLoc.put(folderName, context.getLocation(document.getDocumentElement()));
        }
    }

    private void collectIds(Element element, Set<String> ids) {
        if (element == null) return;
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            String cleanId = id.substring(id.lastIndexOf('/') + 1);
            ids.add(cleanId);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                collectIds((Element) child, ids);
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UElement node,
            @com.android.annotations.NonNull com.android.resources.ResourceType type,
            @com.android.annotations.NonNull String name,
            boolean isFramework) {
        if (type == com.android.resources.ResourceType.LAYOUT) {
            referencedLayouts.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@com.android.annotations.NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : layoutToFolderToIds.entrySet()) {
            String layoutName = entry.getKey();

            if (!referencedLayouts.isEmpty() && !referencedLayouts.contains(layoutName)) {
                continue;
            }

            Map<String, Set<String>> folderToIds = entry.getValue();
            if (folderToIds.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<String, Set<String>> folderEntry : folderToIds.entrySet()) {
                String folder = folderEntry.getKey();
                Set<String> ids = folderEntry.getValue();

                List<String> missing = new ArrayList<>();
                for (String id : allIds) {
                    if (!ids.contains(id)) {
                        missing.add(id);
                    }
                }

                if (!missing.isEmpty()) {
                    Location location = layoutToFolderToLocation.get(layoutName).get(folder);
                    if (location != null) {
                        Collections.sort(missing);
                        String message = String.format(
                                "Layout `%s` in `%s` is missing the following views: %s",
                                layoutName, folder, missing.toString());
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }
}