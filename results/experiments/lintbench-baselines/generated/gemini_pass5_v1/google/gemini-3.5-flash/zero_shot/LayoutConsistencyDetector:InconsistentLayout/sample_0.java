package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LayoutConsistencyDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "InconsistentLayout",
        "Inconsistent Layouts",
        "This check ensures that a layout resource which is defined in multiple "
            + "resource folders, specifies the same set of widgets.\n\n"
            + "This finds cases where you have accidentally forgotten to add "
            + "a widget to all variations of the layout, which could result "
            + "in a runtime crash for some resource configurations when a "
            + "`findViewById()` fails.\n\n"
            + "There are cases where this is intentional. For example, you "
            + "may have a dedicated large tablet layout which adds some extra "
            + "widgets that are not present in the phone version of the layout. "
            + "As long as the code accessing the layout resource is careful to "
            + "handle this properly, it is valid. In that case, you can suppress "
            + "this lint check for the given extra or missing views, or the whole "
            + "layout.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, Map<File, Map<String, Location>>> layoutToIdLocations = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ID);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        String layoutName = context.file.getName();
        int dot = layoutName.lastIndexOf('.');
        if (dot != -1) {
            layoutName = layoutName.substring(0, dot);
        }

        Map<File, Map<String, Location>> filesMap = layoutToIdLocations.get(layoutName);
        if (filesMap == null) {
            filesMap = new LinkedHashMap<>();
            layoutToIdLocations.put(layoutName, filesMap);
        }

        File file = context.file;
        if (!filesMap.containsKey(file)) {
            filesMap.put(file, new LinkedHashMap<>());
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (!SdkConstants.ATTR_ID.equals(name)) {
            return;
        }

        String value = attribute.getValue();
        String id = value;
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            id = id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            id = id.substring(SdkConstants.ID_PREFIX.length());
        }

        String layoutName = context.file.getName();
        int dot = layoutName.lastIndexOf('.');
        if (dot != -1) {
            layoutName = layoutName.substring(0, dot);
        }

        Map<File, Map<String, Location>> filesMap = layoutToIdLocations.get(layoutName);
        if (filesMap == null) {
            filesMap = new LinkedHashMap<>();
            layoutToIdLocations.put(layoutName, filesMap);
        }

        File file = context.file;
        Map<String, Location> idsMap = filesMap.get(file);
        if (idsMap == null) {
            idsMap = new LinkedHashMap<>();
            filesMap.put(file, idsMap);
        }

        Location location = context.getLocation(attribute);
        idsMap.put(id, location);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Map<File, Map<String, Location>>> entry : layoutToIdLocations.entrySet()) {
            Map<File, Map<String, Location>> filesMap = entry.getValue();
            if (filesMap.size() <= 1) {
                continue;
            }

            for (Map.Entry<File, Map<String, Location>> currentEntry : filesMap.entrySet()) {
                File currentFile = currentEntry.getKey();
                Map<String, Location> currentIds = currentEntry.getValue();

                for (Map.Entry<String, Location> idEntry : currentIds.entrySet()) {
                    String id = idEntry.getKey();
                    Location location = idEntry.getValue();
                    List<String> missingInFolders = new ArrayList<>();

                    for (Map.Entry<File, Map<String, Location>> otherEntry : filesMap.entrySet()) {
                        File otherFile = otherEntry.getKey();
                        if (otherFile.equals(currentFile)) {
                            continue;
                        }
                        Map<String, Location> otherIds = otherEntry.getValue();
                        if (!otherIds.containsKey(id)) {
                            File parent = otherFile.getParentFile();
                            String folderName = parent != null ? parent.getName() : "other layout folder";
                            missingInFolders.add(folderName);
                        }
                    }

                    if (!missingInFolders.isEmpty()) {
                        Collections.sort(missingInFolders);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < missingInFolders.size(); i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(missingInFolders.get(i));
                        }
                        String foldersStr = sb.toString();
                        String message = String.format(
                            "The view ID `%s` is defined in this layout but is missing in: %s",
                            id, foldersStr
                        );
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
        layoutToIdLocations.clear();
    }
}