package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class LayoutConsistencyDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private final Map<String, List<LayoutInfo>> mLayoutInfos = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders, "
                    + "specifies the same set of widgets. This finds cases where you have accidentally "
                    + "forgotten to add a widget to all variations of the layout, which could result in a "
                    + "runtime crash for some resource configurations when a `findViewById()` fails.\n\n"
                    + "There are cases where this is intentional. For example, you may have a dedicated "
                    + "large tablet layout which adds some extra widgets that are not present in the phone "
                    + "version of the layout. As long as the code accessing the layout resource is careful "
                    + "to handle this properly, it is valid. In that case, you can suppress this lint check "
                    + "for the given extra or missing views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mLayoutInfos.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        LayoutInfo info = new LayoutInfo(context.file);
        mLayoutInfos.computeIfAbsent(context.file.getName(), k -> new ArrayList<>()).add(info);

        info.rootLocation = context.getLocation(root);
        collectIds(context, root, info);
    }

    private static void collectIds(@NonNull XmlContext context, @NonNull Element element,
            @NonNull LayoutInfo info) {
        String id = element.getAttributeNS(ANDROID_URI, "id");
        if (id != null && !id.isEmpty()) {
            String stripped = LintUtils.stripIdPrefix(id);
            info.ids.add(stripped);
            info.idLocations.put(stripped, context.getLocation(element));
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds(context, (Element) child, info);
            }
            child = child.getNextSibling();
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : mLayoutInfos.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutInfo> variants = entry.getValue();
            if (variants.size() < 2) {
                continue;
            }

            Set<String> common = null;
            for (LayoutInfo info : variants) {
                if (common == null) {
                    common = new HashSet<>(info.ids);
                } else {
                    common.retainAll(info.ids);
                }
            }

            if (common == null) {
                continue;
            }

            for (LayoutInfo info : variants) {
                Set<String> inconsistent = new HashSet<>(info.ids);
                inconsistent.removeAll(common);
                for (String id : inconsistent) {
                    Location location = info.idLocations.get(id);
                    if (location == null) {
                        location = info.rootLocation;
                    }
                    if (location == null) {
                        location = Location.create(info.file);
                    }
                    String message = String.format(
                            "The view `R.id.%1$s` is not present in all versions of layout `%2$s`; "
                                    + "it appears in `%3$s` but is missing from at least one other "
                                    + "configuration.",
                            id, layoutName, info.file.getName());
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private static class LayoutInfo {
        final File file;
        Location rootLocation;
        final Set<String> ids = new HashSet<>();
        final Map<String, Location> idLocations = new HashMap<>();

        LayoutInfo(File file) {
            this.file = file;
        }
    }
}