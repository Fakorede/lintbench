package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.w3c.dom.Element;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

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

    private final Map<String, Map<File, Set<String>>> layoutToFilesAndIds = new LinkedHashMap<>();
    private final Map<File, Location> fileToRootLocation = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(Context context) {
        String fileName = context.file.getName();
        if (fileName.endsWith(".xml")) {
            String layoutName = fileName.substring(0, fileName.length() - 4);
            layoutToFilesAndIds
                    .computeIfAbsent(layoutName, k -> new LinkedHashMap<>())
                    .put(context.file, new LinkedHashSet<>());
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getParentNode() instanceof org.w3c.dom.Document) {
            fileToRootLocation.put(context.file, context.getNameLocation(element));
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id != null && !id.isEmpty()) {
            String cleanId = id;
            if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
                cleanId = id.substring(SdkConstants.NEW_ID_PREFIX.length());
            } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
                cleanId = id.substring(SdkConstants.ID_PREFIX.length());
            }

            String fileName = context.file.getName();
            if (fileName.endsWith(".xml")) {
                String layoutName = fileName.substring(0, fileName.length() - 4);
                Map<File, Set<String>> filesAndIds = layoutToFilesAndIds.get(layoutName);
                if (filesAndIds != null) {
                    Set<String> ids = filesAndIds.get(context.file);
                    if (ids != null) {
                        ids.add(cleanId);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, Map<File, Set<String>>> entry : layoutToFilesAndIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<File, Set<String>> fileToIds = entry.getValue();
            if (fileToIds.size() <= 1) {
                continue;
            }

            Set<String> allIds = new LinkedHashSet<>();
            for (Set<String> ids : fileToIds.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<File, Set<String>> fileEntry : fileToIds.entrySet()) {
                File file = fileEntry.getKey();
                Set<String> ids = fileEntry.getValue();

                Set<String> missing = new TreeSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    Map<String, List<String>> missingIdToDefiningFolders = new LinkedHashMap<>();
                    for (String missingId : missing) {
                        for (Map.Entry<File, Set<String>> otherFileEntry : fileToIds.entrySet()) {
                            if (otherFileEntry.getKey().equals(file)) {
                                continue;
                            }
                            if (otherFileEntry.getValue().contains(missingId)) {
                                String folderName = otherFileEntry.getKey().getParentFile().getName();
                                missingIdToDefiningFolders
                                        .computeIfAbsent(missingId, k -> new ArrayList<>())
                                        .add(folderName);
                            }
                        }
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Layout \"").append(layoutName).append("\" in \"")
                      .append(file.getParentFile().getName()).append("\" is missing the following IDs: ");

                    boolean first = true;
                    for (String missingId : missing) {
                        if (!first) {
                            sb.append(", ");
                        }
                        first = false;
                        sb.append(missingId);
                        List<String> folders = missingIdToDefiningFolders.get(missingId);
                        if (folders != null && !folders.isEmpty()) {
                            sb.append(" (defined in ").append(String.join(", ", folders)).append(")");
                        }
                    }

                    Location location = fileToRootLocation.get(file);
                    if (location == null) {
                        location = Location.create(file);
                    }
                    context.report(ISSUE, location, sb.toString());
                }
            }
        }
    }
}