package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
import java.io.File;
import java.util.ArrayList;
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
                            + "resource folders, specifies the same set of widgets.\n\n"
                            + "This finds cases where you have accidentally forgotten to add "
                            + "a widget to all variations of the layout, which could result "
                            + "in a runtime crash for some resource configurations when a "
                            + "`findViewById()` fails.\n\n"
                            + "There **are** cases where this is intentional. For example, you "
                            + "may have a dedicated large tablet layout which adds some extra "
                            + "widgets that are not present in the phone version of the layout. "
                            + "As long as the code accessing the layout resource is careful to "
                            + "handle this properly, it is valid. In that case, you can suppress "
                            + "this lint check for the given extra or missing views, or the whole "
                            + "layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            Scope.RESOURCE_AND_SOURCE_SET));

    private final Map<String, List<LayoutInfo>> layouts = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - ".xml".length());
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(document);

        synchronized (layouts) {
            List<LayoutInfo> list = layouts.computeIfAbsent(layoutName, k -> new ArrayList<>());
            list.add(new LayoutInfo(folderName, ids, location));
        }
    }

    private void collectIds(org.w3c.dom.Node node, Set<String> ids) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) node;
            String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
            if (id != null && !id.isEmpty()) {
                String cleanId = id.substring(id.indexOf('/') + 1);
                if (!cleanId.isEmpty()) {
                    ids.add(cleanId);
                }
            }
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectIds(children.item(i), ids);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : layouts.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutInfo> infos = entry.getValue();
            if (infos.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutInfo info : infos) {
                allIds.addAll(info.ids);
            }

            for (LayoutInfo info : infos) {
                List<String> missing = new ArrayList<>();
                for (String id : allIds) {
                    if (!info.ids.contains(id)) {
                        missing.add(id);
                    }
                }
                if (!missing.isEmpty()) {
                    for (String missingId : missing) {
                        List<String> presentIn = new ArrayList<>();
                        for (LayoutInfo other : infos) {
                            if (other.ids.contains(missingId)) {
                                presentIn.add(other.folderName);
                            }
                        }
                        String presentInStr = String.join(", ", presentIn);
                        String message = String.format(
                                "Layout \"%s\" in %s is missing ID \"%s\" (defined in %s)",
                                layoutName,
                                info.folderName,
                                missingId,
                                presentInStr);
                        context.report(ISSUE, info.location, message);
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UElement node,
            @NonNull com.android.resources.ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        // Implemented to satisfy SourceCodeScanner interface requirement.
        // Consistency checks are completed globally in afterCheckRootProject.
    }

    private static class LayoutInfo {
        final String folderName;
        final Set<String> ids;
        final Location location;

        LayoutInfo(String folderName, Set<String> ids, Location location) {
            this.folderName = folderName;
            this.ids = ids;
            this.location = location;
        }
    }
}