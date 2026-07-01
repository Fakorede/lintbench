package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource "
                    + "folders specifies the same set of widgets. This finds cases where you "
                    + "have accidentally forgotten to add a widget to all variations of the "
                    + "layout, which could result in a runtime crash for some resource "
                    + "configurations when a `findViewById()` fails.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, List<LayoutInfo>> mLayoutNameToInfos = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLayoutNameToInfos.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        String id = getIdName(value);
        if (id == null) {
            return;
        }

        Node node = attribute.getOwnerElement();
        if (node == null) {
            return;
        }

        String layoutName = context.file.getName();
        LayoutInfo info = getInfo(layoutName, context);
        info.ids.add(id);
        info.idToTag.put(id, node.getNodeName());
        info.idToNode.put(id, node);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : mLayoutNameToInfos.entrySet()) {
            List<LayoutInfo> infos = entry.getValue();
            if (infos.size() < 2) {
                continue;
            }

            for (LayoutInfo info : infos) {
                Set<String> others = new HashSet<>();
                for (LayoutInfo other : infos) {
                    if (other != info) {
                        others.addAll(other.ids);
                    }
                }

                Set<String> missing = new HashSet<>(others);
                missing.removeAll(info.ids);

                Set<String> extra = new HashSet<>(info.ids);
                extra.removeAll(others);

                Set<String> typeMismatches = new HashSet<>();
                for (String id : info.ids) {
                    if (!others.contains(id)) {
                        continue;
                    }
                    String tag = info.idToTag.get(id);
                    for (LayoutInfo other : infos) {
                        if (other == info || !other.ids.contains(id)) {
                            continue;
                        }
                        String otherTag = other.idToTag.get(id);
                        if (otherTag != null && !otherTag.equals(tag)) {
                            typeMismatches.add(id + " (" + tag + " vs " + otherTag + ")");
                            break;
                        }
                    }
                }

                if (missing.isEmpty() && extra.isEmpty() && typeMismatches.isEmpty()) {
                    continue;
                }

                String configName = info.file.getParentFile().getName();
                StringBuilder message = new StringBuilder();
                message.append("Layout \"").append(entry.getKey())
                        .append("\" has inconsistent widgets in configuration \"")
                        .append(configName).append("\".");
                if (!missing.isEmpty()) {
                    message.append(" Missing views that are present in other configurations: ")
                            .append(missing).append(".");
                }
                if (!extra.isEmpty()) {
                    message.append(" Extra views that are not present in other configurations: ")
                            .append(extra).append(".");
                }
                if (!typeMismatches.isEmpty()) {
                    message.append(" Views with the same id but different types in other configurations: ")
                            .append(typeMismatches).append(".");
                }

                Node root = info.context.document.getDocumentElement();
                Location location = root != null
                        ? info.context.getLocation(root)
                        : info.context.getLocation(info.idToNode.values().iterator().next());
                info.context.report(ISSUE, location, message.toString());
            }
        }
    }

    private LayoutInfo getInfo(@NonNull String layoutName, @NonNull XmlContext context) {
        List<LayoutInfo> list = mLayoutNameToInfos.get(layoutName);
        if (list == null) {
            list = new ArrayList<>();
            mLayoutNameToInfos.put(layoutName, list);
        }

        for (LayoutInfo info : list) {
            if (info.file.equals(context.file)) {
                return info;
            }
        }

        LayoutInfo info = new LayoutInfo(context.file, context);
        list.add(info);
        return info;
    }

    private static String getIdName(@NonNull String value) {
        if (value.startsWith("@+id/")) {
            return value.substring(5);
        } else if (value.startsWith("@id/")) {
            return value.substring(4);
        }
        return null;
    }

    private static final class LayoutInfo {
        final File file;
        final XmlContext context;
        final Set<String> ids = new HashSet<>();
        final Map<String, String> idToTag = new HashMap<>();
        final Map<String, Node> idToNode = new HashMap<>();

        LayoutInfo(File file, XmlContext context) {
            this.file = file;
            this.context = context;
        }
    }
}