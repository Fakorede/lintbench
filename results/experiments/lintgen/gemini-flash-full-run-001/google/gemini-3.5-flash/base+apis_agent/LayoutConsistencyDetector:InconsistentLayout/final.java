package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

    private final Map<String, List<LayoutInfo>> layoutMap = new ConcurrentHashMap<>();

    private static class LayoutInfo {
        final File file;
        final String folderName;
        final Location rootLocation;
        final Map<String, Location> idLocations = new HashMap<>();

        LayoutInfo(File file, String folderName, Location rootLocation) {
            this.file = file;
            this.folderName = folderName;
            this.rootLocation = rootLocation;
        }
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        layoutMap.clear();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String layoutName = LintUtils.getBaseName(context.file.getName());
        String folderName = context.file.getParentFile().getName();

        Location rootLocation = context.getNameLocation(root);
        LayoutInfo info = new LayoutInfo(context.file, folderName, rootLocation);

        collectIds(context, root, info);

        layoutMap.computeIfAbsent(layoutName, k -> Collections.synchronizedList(new ArrayList<>())).add(info);
    }

    private void collectIds(XmlContext context, Element element, LayoutInfo info) {
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String idValue = idAttr.getValue();
            int slash = idValue.indexOf('/');
            String id = slash != -1 ? idValue.substring(slash + 1) : idValue;
            if (!id.isEmpty()) {
                info.idLocations.put(id, context.getValueLocation(idAttr));
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds(context, (Element) child, info);
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, List<LayoutInfo>> entry : layoutMap.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutInfo> infos = entry.getValue();
            if (infos.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (LayoutInfo info : infos) {
                allIds.addAll(info.idLocations.keySet());
            }

            for (LayoutInfo info : infos) {
                for (String id : allIds) {
                    if (!info.idLocations.containsKey(id)) {
                        String definedIn = "";
                        for (LayoutInfo other : infos) {
                            if (other.idLocations.containsKey(id)) {
                                definedIn = other.folderName;
                                break;
                            }
                        }

                        String message = String.format(
                            "Layout `%s` in `%s` is missing ID `%s` (defined in `%s`)",
                            layoutName, info.folderName, id, definedIn
                        );

                        Incident incident = new Incident(ISSUE, info.rootLocation, message);
                        context.report(incident);
                    }
                }
            }
        }
    }
}