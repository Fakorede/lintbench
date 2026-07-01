package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple resource folders, specifies the same set of widgets.\n\n" +
                    "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result " +
                    "in a runtime crash for some resource configurations when a `findViewById()` fails.\n\n" +
                    "There **are** cases where this is intentional. For example, you may have a dedicated large tablet layout which adds some extra " +
                    "widgets that are not present in the phone version of the layout. As long as the code accessing the layout resource is careful to " +
                    "handle this properly, it is valid. In that case, you can suppress this lint check for the given extra or missing views, or the whole layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static class LayoutData {
        final File file;
        final String config;
        final Set<String> ids;

        LayoutData(File file, String config, Set<String> ids) {
            this.file = file;
            this.config = config;
            this.ids = ids;
        }
    }

    private final Map<String, List<LayoutData>> mLayouts = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        String fileName = context.file.getName();
        File parent = context.file.getParentFile();
        String config = parent != null ? parent.getName() : "layout";

        mLayouts.computeIfAbsent(fileName, k -> new ArrayList<>())
                .add(new LayoutData(context.file, config, ids));
    }

    private void collectIds(Element element, Set<String> ids) {
        if (element == null) return;

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, "id");
        if (idAttr != null) {
            String value = idAttr.getValue();
            if (value.startsWith("@+id/")) {
                ids.add(value.substring(5));
            } else if (value.startsWith("@id/")) {
                ids.add(value.substring(4));
            }
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
        for (Map.Entry<String, List<LayoutData>> entry : mLayouts.entrySet()) {
            List<LayoutData> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            LayoutData base = null;
            for (LayoutData data : variants) {
                if ("layout".equals(data.config)) {
                    base = data;
                    break;
                }
            }
            if (base == null) {
                base = variants.get(0);
            }

            for (LayoutData variant : variants) {
                if (variant == base) {
                    continue;
                }

                Set<String> missing = new HashSet<>(base.ids);
                missing.removeAll(variant.ids);
                for (String id : missing) {
                    String message = String.format(
                            "The id \"%s\" is defined in %s but not in %s",
                            id, base.config, variant.config);
                    context.report(ISSUE, Location.create(variant.file), message);
                }

                Set<String> extra = new HashSet<>(variant.ids);
                extra.removeAll(base.ids);
                for (String id : extra) {
                    String message = String.format(
                            "The id \"%s\" is defined in %s but not in %s",
                            id, variant.config, base.config);
                    context.report(ISSUE, Location.create(variant.file), message);
                }
            }
        }
        mLayouts.clear();
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    public void visitResourceReference() {
        // Not applicable for this layout-only detector
    }
}