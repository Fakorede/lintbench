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
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.SdkConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets. This finds cases " +
            "where you have accidentally forgotten to add a widget to all variations " +
            "of the layout, which could result in a runtime crash for some resource " +
            "configurations when a `findViewById()` fails.\n\n" +
            "There are cases where this is intentional. For example, you may have a " +
            "dedicated large tablet layout which adds some extra widgets that are not " +
            "present in the phone version of the layout. As long as the code accessing " +
            "the layout resource is careful to handle this properly, it is valid. In " +
            "that case, you can suppress this lint check for the given extra or " +
            "missing views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<LayoutFileData>> layoutToFiles = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        layoutToFiles.clear();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Set<String> ids = new LinkedHashSet<>();
        collectIds(root, ids);

        String fileName = context.file.getName();
        if (!fileName.endsWith(".xml")) {
            return;
        }
        String layoutName = fileName.substring(0, fileName.length() - ".xml".length());

        Location rootLocation = context.getLocation(root);

        LayoutFileData data = new LayoutFileData(context.file, ids, rootLocation);
        List<LayoutFileData> files = layoutToFiles.computeIfAbsent(layoutName, k -> new ArrayList<>());
        files.add(data);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, List<LayoutFileData>> entry : layoutToFiles.entrySet()) {
            String layoutName = entry.getKey();
            List<LayoutFileData> files = entry.getValue();
            if (files.size() <= 1) {
                continue;
            }

            Set<String> allIds = new LinkedHashSet<>();
            for (LayoutFileData fileData : files) {
                allIds.addAll(fileData.ids);
            }

            for (LayoutFileData fileData : files) {
                Set<String> missing = new LinkedHashSet<>(allIds);
                missing.removeAll(fileData.ids);

                if (!missing.isEmpty()) {
                    for (String missingId : missing) {
                        List<String> definedIn = new ArrayList<>();
                        for (LayoutFileData other : files) {
                            if (other != fileData && other.ids.contains(missingId)) {
                                definedIn.add(other.file.getParentFile().getName() + "/" + other.file.getName());
                            }
                        }

                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < definedIn.size(); i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(definedIn.get(i));
                        }
                        String definedInStr = sb.toString();

                        String message = String.format(
                                "Layout \"%s\" in \"%s\" is missing ID \"%s\" (defined in %s)",
                                layoutName,
                                fileData.file.getParentFile().getName(),
                                missingId,
                                definedInStr
                        );

                        context.report(ISSUE, fileData.rootLocation, message);
                    }
                }
            }
        }
    }

    private void collectIds(@NonNull Element element, @NonNull Set<String> ids) {
        Attr idAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            String cleanId = stripId(id);
            if (cleanId != null) {
                ids.add(cleanId);
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

    @Nullable
    private static String stripId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    private static class LayoutFileData {
        @NonNull final File file;
        @NonNull final Set<String> ids;
        @NonNull final Location rootLocation;

        LayoutFileData(@NonNull File file, @NonNull Set<String> ids, @NonNull Location rootLocation) {
            this.file = file;
            this.ids = ids;
            this.rootLocation = rootLocation;
        }
    }
}