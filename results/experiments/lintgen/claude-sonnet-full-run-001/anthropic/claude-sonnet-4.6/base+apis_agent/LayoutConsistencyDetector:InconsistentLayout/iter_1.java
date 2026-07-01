package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n" +
            "\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n" +
            "\n" +
            "There **are** cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole " +
            "layout",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.ALL_RESOURCES_SCOPE
            ));

    private final Map<String, Map<String, Set<String>>> mLayoutToFolderIds = new HashMap<>();
    private final Map<String, Location> mLocations = new HashMap<>();
    private final Map<String, Location> mIdLocations = new HashMap<>();

    public LayoutConsistencyDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File file = context.file;
        String layoutName = getLayoutName(file);
        String folderName = file.getParentFile().getName();

        Set<String> ids = new HashSet<>();
        Element root = document.getDocumentElement();
        if (root != null) {
            collectIds(root, ids, context, layoutName, folderName);
        }

        Map<String, Set<String>> folderMap = mLayoutToFolderIds.get(layoutName);
        if (folderMap == null) {
            folderMap = new HashMap<>();
            mLayoutToFolderIds.put(layoutName, folderMap);
        }
        folderMap.put(folderName, ids);

        String fileKey = layoutName + ":" + folderName;
        mLocations.put(fileKey, Location.create(file));
    }

    private void collectIds(
            @NonNull Element element,
            @NonNull Set<String> ids,
            @NonNull XmlContext context,
            @NonNull String layoutName,
            @NonNull String folderName) {
        String id = getId(element);
        if (id != null && !id.isEmpty()) {
            ids.add(id);
            String idKey = layoutName + ":" + folderName + ":" + id;
            if (!mIdLocations.containsKey(idKey)) {
                mIdLocations.put(idKey, context.getLocation(element));
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) child, ids, context, layoutName, folderName);
            }
        }
    }

    @Nullable
    private static String getId(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return null;
        }
        Attr idAttr = (Attr) attributes.getNamedItemNS(
                "http://schemas.android.com/apk/res/android", "id");
        if (idAttr == null) {
            return null;
        }
        String value = idAttr.getValue();
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.startsWith("@+id/")) {
            return value.substring("@+id/".length());
        } else if (value.startsWith("@id/")) {
            return value.substring("@id/".length());
        }
        return value;
    }

    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderMap = entry.getValue();

            if (folderMap.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderMap.values()) {
                allIds.addAll(ids);
            }

            if (allIds.isEmpty()) {
                continue;
            }

            List<String> folders = new ArrayList<>(folderMap.keySet());
            Collections.sort(folders);

            for (String folder : folders) {
                Set<String> ids = folderMap.get(folder);
                if (ids == null) {
                    ids = Collections.emptySet();
                }
                Set<String> missing = new HashSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    String fileKey = layoutName + ":" + folder;
                    Location location = mLocations.get(fileKey);
                    if (location == null) {
                        continue;
                    }

                    List<String> missingList = new ArrayList<>(missing);
                    Collections.sort(missingList);

                    StringBuilder sb = new StringBuilder();
                    sb.append("The id-set {");
                    for (int i = 0; i < missingList.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(missingList.get(i));
                    }
                    sb.append("} defined in the other layout variations is not present in this layout (");
                    sb.append(folder);
                    sb.append(")");

                    Location secondary = null;
                    for (String missingId : missingList) {
                        for (String otherFolder : folders) {
                            if (otherFolder.equals(folder)) {
                                continue;
                            }
                            Set<String> otherIds = folderMap.get(otherFolder);
                            if (otherIds != null && otherIds.contains(missingId)) {
                                String idKey = layoutName + ":" + otherFolder + ":" + missingId;
                                Location idLocation = mIdLocations.get(idKey);
                                if (idLocation != null) {
                                    Location copy = Location.create(idLocation.getFile(),
                                            idLocation.getStart(), idLocation.getEnd());
                                    copy.setMessage("Defined here in " + otherFolder);
                                    if (secondary == null) {
                                        secondary = copy;
                                    } else {
                                        Location tail = secondary;
                                        while (tail.getSecondary() != null) {
                                            tail = tail.getSecondary();
                                        }
                                        tail.setSecondary(copy);
                                    }
                                }
                                break;
                            }
                        }
                    }

                    if (secondary != null) {
                        location.setSecondary(secondary);
                    }

                    context.report(ISSUE, location, sb.toString());
                }
            }
        }
    }
}