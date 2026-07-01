package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceReference;
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

    private static final String ATTR_ID = "android:id";

    private final Map<String, List<LayoutDefinition>> mDocumentMap = new HashMap<>();
    private final Set<String> mUsedIds = new HashSet<>();

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource "
                            + "folders specifies the same set of widgets. This finds cases where you have "
                            + "accidentally forgotten to add a widget to all variations of the layout, which could "
                            + "result in a runtime crash for some resource configurations when a findViewById() "
                            + "fails. There are cases where this is intentional; in that case you can suppress "
                            + "this lint check for the given extra or missing views, or the whole layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDocumentMap.clear();
        mUsedIds.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        List<LayoutDefinition> definitions = mDocumentMap.get(name);
        if (definitions == null) {
            definitions = new ArrayList<>();
            mDocumentMap.put(name, definitions);
        }
        definitions.add(new LayoutDefinition(name, file, ids));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutDefinition>> entry : mDocumentMap.entrySet()) {
            List<LayoutDefinition> definitions = entry.getValue();
            if (definitions.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutDefinition definition : definitions) {
                allIds.addAll(definition.ids);
            }

            for (LayoutDefinition definition : definitions) {
                List<String> missing = new ArrayList<>();
                for (String id : allIds) {
                    if (!definition.ids.contains(id) && mUsedIds.contains(id)) {
                        missing.add(id);
                    }
                }

                if (missing.isEmpty()) {
                    continue;
                }

                StringBuilder sb = new StringBuilder();
                for (String id : missing) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append("R.id.").append(id);
                }

                String folder = definition.file.getParentFile() != null
                        ? definition.file.getParentFile().getName()
                        : "?";

                String message = String.format(
                        "The following ids are defined in other configurations of the `%1$s` layout but not in `%2$s`: %3$s. "
                                + "If any of them are accessed via findViewById, this could cause a crash.",
                        entry.getKey(),
                        folder,
                        sb.toString());

                context.report(ISSUE, Location.create(definition.file), message);
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @NonNull ResourceReference reference) {
        if (reference.getType() == ResourceType.ID) {
            mUsedIds.add(reference.getName());
        }
    }

    private static void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        String idName = getIdName(element.getAttribute(ATTR_ID));
        if (idName != null) {
            ids.add(idName);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child != null && child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids);
            }
        }
    }

    private static String getIdName(String idValue) {
        if (idValue == null || idValue.isEmpty()) {
            return null;
        }

        int slash = idValue.lastIndexOf('/');
        if (slash == -1 || slash == idValue.length() - 1) {
            return null;
        }

        return idValue.substring(slash + 1);
    }

    private static class LayoutDefinition {
        final String name;
        final File file;
        final Set<String> ids;

        LayoutDefinition(String name, File file, Set<String> ids) {
            this.name = name;
            this.file = file;
            this.ids = ids;
        }
    }
}