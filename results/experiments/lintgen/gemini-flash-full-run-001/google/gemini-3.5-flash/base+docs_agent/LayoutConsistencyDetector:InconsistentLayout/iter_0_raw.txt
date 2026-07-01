package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n\n" +
            "There **are** cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole " +
            "layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<LayoutInfo>> layoutMap = new TreeMap<>();

    private static class LayoutInfo {
        final File file;
        final Set<String> ids;
        final Location location;

        LayoutInfo(File file, Set<String> ids, Location location) {
            this.file = file;
            this.ids = ids;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        layoutMap.clear();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String layoutName = dot != -1 ? name.substring(0, dot) : name;

        Set<String> ids = new HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        Location location = context.getLocation(document.getDocumentElement());
        List<LayoutInfo> infos = layoutMap.computeIfAbsent(layoutName, k -> new ArrayList<>());
        infos.add(new LayoutInfo(file, ids, location));
    }

    private void collectIds(Node node, Set<String> ids) {
        if (node instanceof Element) {
            Element element = (Element) node;
            String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
            if (id != null && !id.isEmpty()) {
                ids.add(stripIdPrefix(id));
            }
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectIds(children.item(i), ids);
            }
        }
    }

    private static String stripIdPrefix(String id) {
        if (id == null) {
            return "";
        }
        int index = id.indexOf('/');
        if (index != -1) {
            return id.substring(index + 1);
        }
        return id;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : layoutMap.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutInfo> infos = entry.getValue();
            if (infos.size() <= 1) {
                continue;
            }

            infos.sort((o1, o2) -> o1.file.compareTo(o2.file));

            Set<String> allIds = new HashSet<>();
            for (LayoutInfo info : infos) {
                allIds.addAll(info.ids);
            }

            for (LayoutInfo info : infos) {
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(info.ids);

                if (!missingIds.isEmpty()) {
                    List<String> sortedMissing = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissing);
                    String missingList = String.join(", ", sortedMissing);

                    String message = String.format(
                            "Layout `%s` in `%s` is missing the following IDs: %s",
                            layoutName,
                            info.file.getParentFile().getName(),
                            missingList
                    );

                    context.report(
                            ISSUE,
                            info.location,
                            message
                    );
                }
            }
        }
        layoutMap.clear();
    }
}