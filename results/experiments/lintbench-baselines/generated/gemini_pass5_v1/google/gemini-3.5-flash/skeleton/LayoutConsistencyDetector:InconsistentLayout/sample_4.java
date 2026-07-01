package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                            + "resource folders, specifies the same set of widgets.\n\n"
                            + "This finds cases where you have accidentally forgotten to add "
                            + "a widget to all variations of the layout, which could result "
                            + "in a runtime crash for some resource configurations when a "
                            + "`findViewById()` fails.\n\n"
                            + "There are cases where this is intentional. For example, you "
                            + "may have a dedicated large tablet layout which adds some extra "
                            + "widgets that are not present in the phone version of the layout. "
                            + "As long as the code accessing the layout resource is careful to "
                            + "handle this properly, it is valid. In that case, you can suppress "
                            + "this lint check for the given extra or missing views, or the whole "
                            + "layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<LayoutInfo>> layouts = new HashMap<>();

    private static class LayoutInfo {
        @NonNull final String folder;
        @NonNull final File file;
        @NonNull final Set<String> ids;

        LayoutInfo(@NonNull String folder, @NonNull File file, @NonNull Set<String> ids) {
            this.folder = folder;
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
        if (document == null || document.getDocumentElement() == null) {
            return;
        }
        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - 4);
        String folderName = context.file.getParentFile().getName();

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        synchronized (layouts) {
            List<LayoutInfo> list = layouts.get(layoutName);
            if (list == null) {
                list = new ArrayList<>();
                layouts.put(layoutName, list);
            }
            list.add(new LayoutInfo(folderName, context.file, ids));
        }
    }

    private void collectIds(Node node, Set<String> ids) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
            if (id != null && !id.isEmpty()) {
                String cleanId = id.substring(id.indexOf('/') + 1);
                if (!cleanId.isEmpty()) {
                    ids.add(cleanId);
                }
            }
        }
        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                collectIds(children.item(i), ids);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        synchronized (layouts) {
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
                    Set<String> missing = new HashSet<>(allIds);
                    missing.removeAll(info.ids);
                    if (!missing.isEmpty()) {
                        Map<String, List<String>> idToFolders = new HashMap<>();
                        for (String mId : missing) {
                            List<String> folders = new ArrayList<>();
                            for (LayoutInfo other : infos) {
                                if (other != info && other.ids.contains(mId)) {
                                    folders.add(other.folder);
                                }
                            }
                            idToFolders.put(mId, folders);
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append("Layout `").append(layoutName).append("` is inconsistent: ");
                        boolean first = true;
                        for (String mId : missing) {
                            if (!first) {
                                sb.append(", ");
                            }
                            first = false;
                            sb.append("`@id/").append(mId).append("` is missing (defined in ");
                            List<String> folders = idToFolders.get(mId);
                            if (folders != null) {
                                for (int k = 0; k < folders.size(); k++) {
                                    if (k > 0) {
                                        sb.append(", ");
                                    }
                                    sb.append("`").append(folders.get(k)).append("`");
                                }
                            }
                            sb.append(")");
                        }
                        context.report(
                                ISSUE,
                                com.android.tools.lint.detector.api.Location.create(info.file),
                                sb.toString());
                    }
                }
            }
            layouts.clear();
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Override
    public void visitResourceReference(
            @NonNull com.android.tools.lint.detector.api.JavaContext context,
            @NonNull org.jetbrains.uast.UElement node,
            @NonNull com.android.resources.ResourceType type,
            @NonNull String name,
            boolean isWrite) {
    }
}