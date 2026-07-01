package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource "
                    + "folders specifies the same set of widgets. This finds cases where you have "
                    + "accidentally forgotten to add a widget to all variations of the layout, "
                    + "which could result in a runtime crash for some resource configurations when "
                    + "a `findViewById()` fails.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private final Map<String, List<LayoutInfo>> mLayoutGroups = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mLayoutGroups.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!(element.getParentNode() instanceof Document)) {
            return;
        }

        List<ViewEntry> views = collectViews(element);

        File file = context.file;
        File folder = file.getParentFile();
        String config = folder != null ? folder.getName() : "";
        String projectPath = context.getProject().getDir().getAbsolutePath();
        String key = projectPath + "/" + file.getName();

        mLayoutGroups.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new LayoutInfo(context, element, config, views));
    }

    @NonNull
    private static List<ViewEntry> collectViews(@NonNull Element element) {
        List<ViewEntry> views = new ArrayList<>();
        collectViews(element, views);
        return views;
    }

    private static void collectViews(@NonNull Element element, @NonNull List<ViewEntry> views) {
        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            int index = id.indexOf('/');
            if (index != -1 && index < id.length() - 1) {
                views.add(new ViewEntry(id.substring(index + 1)));
            }
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectViews((Element) child, views);
            }
            child = child.getNextSibling();
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : mLayoutGroups.entrySet()) {
            List<LayoutInfo> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutInfo info : group) {
                for (ViewEntry view : info.views) {
                    allIds.add(view.id);
                }
            }

            for (String id : allIds) {
                List<LayoutInfo> missing = new ArrayList<>();
                for (LayoutInfo info : group) {
                    if (!containsId(info.views, id)) {
                        missing.add(info);
                    }
                }

                if (missing.isEmpty() || missing.size() == group.size()) {
                    continue;
                }

                String layoutName = new File(entry.getKey()).getName();
                String message = buildMessage(layoutName, id, missing);
                Location location = missing.get(0).context.getLocation(missing.get(0).root);
                context.report(ISSUE, location, message);
            }
        }

        mLayoutGroups.clear();
    }

    private static boolean containsId(@NonNull List<ViewEntry> views, @NonNull String id) {
        for (ViewEntry view : views) {
            if (id.equals(view.id)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String buildMessage(@NonNull String layoutName, @NonNull String id,
            @NonNull List<LayoutInfo> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("Inconsistent layout '").append(layoutName).append("': ");
        sb.append("the widget with id '@id/").append(id).append("' is missing from ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                if (i == missing.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(missing.get(i).config).append('/').append(layoutName);
        }
        return sb.toString();
    }

    private static class LayoutInfo {
        final XmlContext context;
        final Element root;
        final String config;
        final List<ViewEntry> views;

        LayoutInfo(@NonNull XmlContext context, @NonNull Element root,
                @NonNull String config, @NonNull List<ViewEntry> views) {
            this.context = context;
            this.root = root;
            this.config = config;
            this.views = views;
        }
    }

    private static class ViewEntry {
        final String id;

        ViewEntry(@NonNull String id) {
            this.id = id;
        }
    }
}