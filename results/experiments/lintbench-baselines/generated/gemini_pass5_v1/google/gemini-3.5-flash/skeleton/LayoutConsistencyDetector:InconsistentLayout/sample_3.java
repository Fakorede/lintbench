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
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.RESOURCE_FILE));

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

    private final Map<String, List<LayoutInfo>> layouts = new HashMap<>();

    private static class LayoutInfo {
        final File file;
        final Set<String> ids;

        LayoutInfo(File file, Set<String> ids) {
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
        Element root = document.getDocumentElement();
        if (root != null) {
            Set<String> ids = new HashSet<>();
            collectIds(root, ids);
            String layoutName = context.file.getName();
            List<LayoutInfo> list = layouts.computeIfAbsent(layoutName, k -> new ArrayList<>());
            list.add(new LayoutInfo(context.file, ids));
        }
    }

    private void collectIds(Element element, Set<String> ids) {
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            String cleanId = id;
            int slash = id.indexOf('/');
            if (slash != -1) {
                cleanId = id.substring(slash + 1);
            }
            ids.add(cleanId);
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
                for (String id : allIds) {
                    if (!info.ids.contains(id)) {
                        String definedIn = "";
                        for (LayoutInfo other : infos) {
                            if (other.ids.contains(id)) {
                                definedIn = other.file.getParentFile().getName();
                                break;
                            }
                        }
                        String message = String.format(
                                "Layout `%s` is missing ID `%s` (defined in `%s`)",
                                layoutName, id, definedIn);
                        context.report(ISSUE, Location.create(info.file), message);
                    }
                }
            }
        }
        layouts.clear();
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    public void visitResourceReference() {
        // Unused as appliesToResourceRefs returns false
    }
}