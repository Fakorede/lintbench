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
import com.android.tools.lint.detector.api.SourceCodeScanner;
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

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource folders, specifies the same set of widgets.",
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
        String fileName = context.file.getName();
        if (fileName.endsWith(".xml")) {
            String layoutName = fileName.substring(0, fileName.length() - 4);
            Set<String> ids = new HashSet<>();
            Element root = document.getDocumentElement();
            if (root != null) {
                collectIds(root, ids);
            }
            synchronized (layouts) {
                List<LayoutInfo> list = layouts.get(layoutName);
                if (list == null) {
                    list = new ArrayList<>();
                    layouts.put(layoutName, list);
                }
                list.add(new LayoutInfo(context.file, ids));
            }
        }
    }

    private void collectIds(Node node, Set<String> ids) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
            if (id == null || id.isEmpty()) {
                id = element.getAttribute("android:id");
            }
            if (id != null && !id.isEmpty()) {
                String cleanId = stripIdPrefix(id);
                if (!cleanId.isEmpty()) {
                    ids.add(cleanId);
                }
            }
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectIds(children.item(i), ids);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : layouts.entrySet()) {
            List<LayoutInfo> infos = entry.getValue();
            if (infos.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutInfo info : infos) {
                allIds.addAll(info.ids);
            }

            for (String id : allIds) {
                List<LayoutInfo> presentIn = new ArrayList<>();
                List<LayoutInfo> missingIn = new ArrayList<>();
                for (LayoutInfo info : infos) {
                    if (info.ids.contains(id)) {
                        presentIn.add(info);
                    } else {
                        missingIn.add(info);
                    }
                }

                if (!missingIn.isEmpty() && !presentIn.isEmpty()) {
                    for (LayoutInfo missing : missingIn) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < presentIn.size(); i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(getDisplayPath(presentIn.get(i).file));
                        }
                        String message = String.format(
                                "The view `%s` is defined in %s but missing in %s",
                                id, sb.toString(), getDisplayPath(missing.file));

                        Location location = Location.create(missing.file);
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    private static String getDisplayPath(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName() + "/" + file.getName();
        }
        return file.getName();
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
            boolean isFramework) {
        // No-op
    }
}