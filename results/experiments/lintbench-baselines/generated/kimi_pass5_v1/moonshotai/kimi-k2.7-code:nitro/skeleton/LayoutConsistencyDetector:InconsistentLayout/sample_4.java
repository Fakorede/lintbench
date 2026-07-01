package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceReference;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders specifies the same set of widgets. "
                            + "If a layout variation defines an id that is missing from another "
                            + "variation, a `findViewById` call on that configuration can fail "
                            + "at runtime.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<LayoutDefinition>> mLayoutDefinitions = new HashMap<>();
    private final Set<String> mReferencedLayouts = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String folderName = context.file.getParentFile().getName();

        List<LayoutDefinition> list = mLayoutDefinitions.get(layoutName);
        if (list == null) {
            list = new ArrayList<>();
            mLayoutDefinitions.put(layoutName, list);
        }
        list.add(new LayoutDefinition(folderName, ids, context.getLocation(context.file)));
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceReference reference) {
        if (reference.getResourceType() == ResourceType.LAYOUT) {
            mReferencedLayouts.add(reference.getName());
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String layoutName : mReferencedLayouts) {
            List<LayoutDefinition> definitions = mLayoutDefinitions.get(layoutName);
            if (definitions == null || definitions.size() < 2) {
                continue;
            }

            Set<String> common = null;
            for (LayoutDefinition definition : definitions) {
                if (common == null) {
                    common = new HashSet<>(definition.ids);
                } else {
                    common.retainAll(definition.ids);
                }
            }

            for (LayoutDefinition definition : definitions) {
                Set<String> onlyHere = new HashSet<>(definition.ids);
                onlyHere.removeAll(common);
                if (!onlyHere.isEmpty()) {
                    String message =
                            String.format(
                                    "The layout \"%1$s\" has ids that are not present in all of "
                                            + "its variations. The \"%2$s\" version defines the "
                                            + "following extra ids: %3$s. This can cause "
                                            + "findViewById(R.id.<id>) to fail at runtime on "
                                            + "configurations that do not include them.",
                                    layoutName, definition.folder, onlyHere);
                    context.report(ISSUE, definition.location, message);
                }
            }
        }

        mLayoutDefinitions.clear();
        mReferencedLayouts.clear();
    }

    private static void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getName();
            if (attrName != null
                    && (attrName.equals("android:id") || attrName.endsWith(":id"))) {
                String value = attr.getNodeValue();
                if (value != null && !value.isEmpty()) {
                    ids.add(stripIdPrefix(value));
                }
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

    private static String stripIdPrefix(String value) {
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static class LayoutDefinition {
        final String folder;
        final Set<String> ids;
        final Location location;

        LayoutDefinition(String folder, Set<String> ids, Location location) {
            this.folder = folder;
            this.ids = ids;
            this.location = location;
        }
    }
}