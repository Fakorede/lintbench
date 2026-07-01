package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";

    public static final Issue INCONSISTENT_LAYOUT = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders, specifies the same set of widgets. This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result in a runtime crash for some resource configurations when a findViewById() fails.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.RESOURCE_FILE))
    );

    private final Map<String, Map<String, FolderInfo>> mLayoutMap = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mLayoutMap.clear();
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        if (!folderName.startsWith("layout")) {
            return;
        }

        String layoutName = file.getName().replaceFirst("\\.xml$", "");
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        Map<String, FolderInfo> folders =
                mLayoutMap.computeIfAbsent(layoutName, k -> new TreeMap<>());
        folders.put(folderName, new FolderInfo(ids, context.getLocation(root)));
    }

    private void collectIds(@NotNull Element element, @NotNull Set<String> ids) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String name = getIdName(id);
            if (name != null) {
                ids.add(name);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    private String getIdName(@NotNull String idValue) {
        int slash = idValue.lastIndexOf('/');
        if (slash != -1 && slash + 1 < idValue.length()) {
            return idValue.substring(slash + 1);
        }
        return idValue;
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (Map.Entry<String, Map<String, FolderInfo>> layoutEntry : mLayoutMap.entrySet()) {
            String layoutName = layoutEntry.getKey();
            Map<String, FolderInfo> folders = layoutEntry.getValue();
            if (folders.size() < 2) {
                continue;
            }

            Set<String> common = null;
            for (FolderInfo info : folders.values()) {
                if (common == null) {
                    common = new HashSet<>(info.ids);
                } else {
                    common.retainAll(info.ids);
                }
            }
            if (common == null) {
                continue;
            }

            boolean consistent = true;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, FolderInfo> entry : folders.entrySet()) {
                String folder = entry.getKey();
                FolderInfo info = entry.getValue();

                Set<String> missing = new HashSet<>(common);
                missing.removeAll(info.ids);
                Set<String> extra = new HashSet<>(info.ids);
                extra.removeAll(common);

                if (!missing.isEmpty() || !extra.isEmpty()) {
                    consistent = false;
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append("in ").append(folder);
                    if (!missing.isEmpty()) {
                        sb.append(" missing ").append(missing);
                    }
                    if (!extra.isEmpty()) {
                        sb.append(" extra ").append(extra);
                    }
                }
            }

            if (!consistent) {
                FolderInfo first = folders.values().iterator().next();
                String message = String.format(
                        "The layout R.layout.%1$s has inconsistent widgets across configurations: %2$s",
                        layoutName, sb);
                context.report(INCONSISTENT_LAYOUT, first.location, message);
            }
        }
    }

    private static class FolderInfo {
        final Set<String> ids;
        final Location location;

        FolderInfo(@NotNull Set<String> ids, @NotNull Location location) {
            this.ids = new HashSet<>(ids);
            this.location = location;
        }
    }
}