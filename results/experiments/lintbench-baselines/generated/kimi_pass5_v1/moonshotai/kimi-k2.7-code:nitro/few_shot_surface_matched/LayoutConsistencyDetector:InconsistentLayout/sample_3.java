package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.resources.ResourceFolderType.LAYOUT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LayoutConsistencyDetector extends LayoutDetector
        implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets.\n"
                            + "\n"
                            + "This finds cases where you have accidentally forgotten to add "
                            + "a widget to all variations of the layout, which could result "
                            + "in a runtime crash for some resource configurations when a "
                            + "`findViewById()` fails.\n"
                            + "\n"
                            + "There are cases where this is intentional. For example, you "
                            + "may have a dedicated large tablet layout which adds some extra "
                            + "widgets that are not present in the phone version of the layout. "
                            + "As long as the code accessing the layout resource is careful to "
                            + "handle this properly, it is valid. In that case, you can suppress "
                            + "this lint check for the given extra or missing views, or the whole "
                            + "layout.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private final Map<String, Map<String, LayoutInfo>> mLayoutToConfigIds = new HashMap<>();
    private final Set<String> mReferencedLayouts = new HashSet<>();

    private static class LayoutInfo {
        final Set<String> ids;
        final Location location;

        LayoutInfo(Set<String> ids, Location location) {
            this.ids = ids;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String layoutName = getLayoutName(context);
        if (layoutName == null) {
            return;
        }

        String configName = getConfigName(context);
        Set<String> ids = new HashSet<>();
        collectIds(root, ids);

        Location location = context.getLocation(root);
        Map<String, LayoutInfo> configs =
                mLayoutToConfigIds.computeIfAbsent(layoutName, k -> new HashMap<>());
        configs.put(configName, new LayoutInfo(ids, location));
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        if (type == ResourceType.LAYOUT && !isFramework) {
            mReferencedLayouts.add(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String layoutName : mReferencedLayouts) {
            Map<String, LayoutInfo> configs = mLayoutToConfigIds.get(layoutName);
            if (configs == null || configs.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            Set<String> commonIds = null;
            for (LayoutInfo info : configs.values()) {
                allIds.addAll(info.ids);
                if (commonIds == null) {
                    commonIds = new HashSet<>(info.ids);
                } else {
                    commonIds.retainAll(info.ids);
                }
            }

            if (commonIds != null && allIds.equals(commonIds)) {
                continue;
            }

            for (Map.Entry<String, LayoutInfo> entry : configs.entrySet()) {
                String configName = entry.getKey();
                LayoutInfo info = entry.getValue();

                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(info.ids);

                Set<String> extra = new HashSet<>(info.ids);
                extra.removeAll(commonIds);

                if (!missing.isEmpty() || !extra.isEmpty()) {
                    StringBuilder message = new StringBuilder();
                    message.append("Layout ")
                            .append(layoutName)
                            .append(" in ")
                            .append(configName)
                            .append(" is inconsistent with other configurations");
                    if (!missing.isEmpty()) {
                        message.append("; missing views: ").append(missing);
                    }
                    if (!extra.isEmpty()) {
                        message.append("; extra views: ").append(extra);
                    }
                    context.report(ISSUE, info.location, message.toString());
                }
            }
        }

        mLayoutToConfigIds.clear();
        mReferencedLayouts.clear();
    }

    private static void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (id != null && !id.isEmpty()) {
            int slash = id.lastIndexOf('/');
            if (slash != -1 && slash < id.length() - 1) {
                ids.add(id.substring(slash + 1));
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

    @Nullable
    private static String getLayoutName(@NonNull XmlContext context) {
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name.isEmpty() ? null : name;
    }

    @NonNull
    private static String getConfigName(@NonNull XmlContext context) {
        return context.file.getParentFile().getName();
    }
}