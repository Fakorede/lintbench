package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(LayoutConsistencyDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This layout is defined in multiple resource folders, but the set of views "
                            + "with android:id attributes is not the same across those versions. "
                            + "This can lead to a runtime crash on some device configurations when "
                            + "a `findViewById()` call fails to find a view that is present in "
                            + "another configuration. If the difference is intentional, you can "
                            + "suppress this warning on the extra or missing views, or on the layout "
                            + "as a whole.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, List<LayoutVariant>> mLayoutMap = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        String fileName = context.file.getName();
        String layoutName = stripExtension(fileName);
        if (layoutName == null || layoutName.isEmpty()) {
            return;
        }

        List<LayoutVariant> variants = mLayoutMap.get(layoutName);
        if (variants == null) {
            variants = new ArrayList<>();
            mLayoutMap.put(layoutName, variants);
        }

        variants.add(new LayoutVariant(
                context.file.getPath(),
                fileName,
                context.getLocation(document),
                ids));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutVariant>> entry : mLayoutMap.entrySet()) {
            List<LayoutVariant> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            Set<String> union = new HashSet<>();
            for (LayoutVariant variant : variants) {
                union.addAll(variant.ids);
            }

            if (union.isEmpty()) {
                continue;
            }

            for (LayoutVariant variant : variants) {
                Set<String> missing = new HashSet<>(union);
                missing.removeAll(variant.ids);
                if (missing.isEmpty()) {
                    continue;
                }

                List<String> sortedMissingIds = new ArrayList<>(missing);
                Collections.sort(sortedMissingIds);

                StringBuilder sb = new StringBuilder();
                for (String id : sortedMissingIds) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append("@+id/").append(id);
                }

                String message = String.format(Locale.US,
                        "The layout \"%1$s\" has an inconsistent set of views: \"%2$s\" is missing "
                                + "the following views that are present in other configurations: %3$s",
                        entry.getKey(),
                        variant.fileName,
                        sb);

                context.report(ISSUE, variant.location, message);
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }

    public void visitResourceReference(
            @NonNull XmlContext context,
            @NonNull ResourceType resourceType,
            @NonNull String name,
            boolean isFramework) {
        // Not used by this detector.
    }

    private void collectIds(@NonNull Node node, @NonNull Set<String> ids) {
        if (node.getNodeType() == Node.ELEMENT_NODE && node instanceof Element) {
            Element element = (Element) node;
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, "id");
            if (attr != null) {
                String id = extractId(attr.getValue());
                if (id != null) {
                    ids.add(id);
                }
            }
        }

        Node child = node.getFirstChild();
        while (child != null) {
            collectIds(child, ids);
            child = child.getNextSibling();
        }
    }

    private static String extractId(@NonNull String value) {
        int slash = value.lastIndexOf('/');
        if (slash == -1 || slash == value.length() - 1) {
            return null;
        }
        return value.substring(slash + 1);
    }

    private static String stripExtension(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static class LayoutVariant {
        final String filePath;
        final String fileName;
        final Location location;
        final Set<String> ids;

        LayoutVariant(
                @NonNull String filePath,
                @NonNull String fileName,
                @NonNull Location location,
                @NonNull Set<String> ids) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.location = location;
            this.ids = ids;
        }
    }
}