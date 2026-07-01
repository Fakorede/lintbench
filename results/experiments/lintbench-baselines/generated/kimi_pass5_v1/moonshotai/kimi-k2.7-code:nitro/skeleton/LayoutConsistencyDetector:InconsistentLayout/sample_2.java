package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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
import org.jetbrains.uast.UElement;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders specifies the same set of widgets. This finds "
                            + "cases where you have accidentally forgotten to add a widget to "
                            + "all variations of the layout, which could result in a runtime "
                            + "crash for some resource configurations when a `findViewById()` "
                            + "fails. There are cases where this is intentional; in that case "
                            + "you can suppress this lint check.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<LayoutEntry>> mLayoutEntries = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutEntries.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || isSuppressed(root)) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        String folderName = context.file.getParentFile().getName();

        Set<String> ids = new HashSet<>();
        collectWidgetIds(root, ids);

        mLayoutEntries.computeIfAbsent(layoutName, k -> new ArrayList<>())
                .add(new LayoutEntry(context.file, folderName, ids));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutEntry>> entry : mLayoutEntries.entrySet()) {
            List<LayoutEntry> entries = entry.getValue();
            if (entries.size() < 2) {
                continue;
            }

            String layoutName = entry.getKey();

            Set<String> common = null;
            Set<String> all = null;
            for (LayoutEntry e : entries) {
                if (common == null) {
                    common = new HashSet<>(e.ids);
                    all = new HashSet<>(e.ids);
                } else {
                    common.retainAll(e.ids);
                    all.addAll(e.ids);
                }
            }

            if (common.equals(all)) {
                continue;
            }

            for (LayoutEntry e : entries) {
                Set<String> missing = new HashSet<>(all);
                missing.removeAll(e.ids);
                for (String id : missing) {
                    String message = String.format(
                            "Layout \"%1$s\" in folder \"%2$s\" is missing the view \"@+id/%3$s\" "
                                    + "which is present in another configuration",
                            layoutName, e.folder, id);
                    context.report(ISSUE, Location.create(e.file), message);
                }

                Set<String> extra = new HashSet<>(e.ids);
                extra.removeAll(common);
                for (String id : extra) {
                    String message = String.format(
                            "Layout \"%1$s\" in folder \"%2$s\" defines the view \"@+id/%3$s\" "
                                    + "which is not present in another configuration",
                            layoutName, e.folder, id);
                    context.report(ISSUE, Location.create(e.file), message);
                }
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        // Not used; this check only compares layout XML files.
    }

    private void collectWidgetIds(@NonNull Element element, @NonNull Set<String> ids) {
        String id = element.getAttributeNS(ANDROID_URI, "id");
        if (id != null && !id.isEmpty()) {
            int slash = id.lastIndexOf('/');
            if (slash != -1) {
                id = id.substring(slash + 1);
            }
            ids.add(id);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectWidgetIds((Element) child, ids);
            }
        }
    }

    private boolean isSuppressed(@NonNull Element root) {
        String ignore = root.getAttributeNS(TOOLS_URI, "ignore");
        if (ignore == null || ignore.isEmpty()) {
            return false;
        }
        for (String token : ignore.split(",")) {
            if (token.trim().equals(ISSUE.getId())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private static class LayoutEntry {
        final File file;
        final String folder;
        final Set<String> ids;

        LayoutEntry(File file, String folder, Set<String> ids) {
            this.file = file;
            this.folder = folder;
            this.ids = ids;
        }
    }
}