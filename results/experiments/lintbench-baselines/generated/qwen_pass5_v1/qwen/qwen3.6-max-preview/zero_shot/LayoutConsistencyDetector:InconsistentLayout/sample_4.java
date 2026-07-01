package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple resource folders specifies the same set of widgets.\n\n" +
            "This finds cases where you have accidentally forgotten to add a widget to all variations of the layout, which could result in a runtime crash for some resource configurations when a `findViewById()` fails.\n\n" +
            "There **are** cases where this is intentional. For example, you may have a dedicated large tablet layout which adds some extra widgets that are not present in the phone version of the layout. As long as the code accessing the layout resource is careful to handle this properly, it is valid. In that case, you can suppress this lint check for the given extra or missing views, or the whole layout.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static class LayoutData {
        final File file;
        final Set<String> ids = new HashSet<>();
        LayoutData(File file) {
            this.file = file;
        }
    }

    private final Map<String, Map<String, LayoutData>> layoutMap = new HashMap<>();

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        String id = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
        if (id == null || id.isEmpty()) {
            return;
        }

        String idName;
        if (id.startsWith(SdkConstants.NEW_ID_PREFIX)) {
            idName = id.substring(SdkConstants.NEW_ID_PREFIX.length());
        } else if (id.startsWith(SdkConstants.ID_PREFIX)) {
            idName = id.substring(SdkConstants.ID_PREFIX.length());
        } else {
            return;
        }

        if (idName.contains(":")) {
            return;
        }

        String fileName = context.file.getName();
        int dotIndex = fileName.indexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;

        File parent = context.file.getParentFile();
        if (parent == null) {
            return;
        }
        String folderName = parent.getName();

        layoutMap.computeIfAbsent(baseName, k -> new HashMap<>())
                 .computeIfAbsent(folderName, k -> new LayoutData(context.file))
                 .ids.add(idName);
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        for (Map.Entry<String, Map<String, LayoutData>> entry : layoutMap.entrySet()) {
            String baseName = entry.getKey();
            Map<String, LayoutData> folders = entry.getValue();

            if (folders.size() < 2) {
                continue;
            }

            String referenceFolder = folders.containsKey(SdkConstants.FD_RES_LAYOUT)
                    ? SdkConstants.FD_RES_LAYOUT
                    : folders.keySet().iterator().next();

            LayoutData referenceData = folders.get(referenceFolder);
            Set<String> referenceIds = referenceData.ids;

            for (Map.Entry<String, LayoutData> folderEntry : folders.entrySet()) {
                String folder = folderEntry.getKey();
                if (folder.equals(referenceFolder)) {
                    continue;
                }

                LayoutData currentData = folderEntry.getValue();
                Set<String> currentIds = currentData.ids;

                for (String id : referenceIds) {
                    if (!currentIds.contains(id)) {
                        String message = String.format(
                                "The id \"%s\" is defined in %s but not in %s",
                                id, referenceFolder, folder);
                        context.report(ISSUE, Location.create(currentData.file), message);
                    }
                }

                for (String id : currentIds) {
                    if (!referenceIds.contains(id)) {
                        String message = String.format(
                                "The id \"%s\" is defined in %s but not in %s",
                                id, folder, referenceFolder);
                        context.report(ISSUE, Location.create(currentData.file), message);
                    }
                }
            }
        }
        layoutMap.clear();
    }
}