package com.android.tools.lint.checks;

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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "InconsistentLayout",
        "Inconsistent Layouts",
        "This check ensures that a layout resource which is defined in multiple " +
        "resource folders specifies the same set of widgets.\n\n" +
        "This finds cases where you have accidentally forgotten to add a widget to all " +
        "variations of the layout, which could result in a runtime crash for some resource " +
        "configurations when a `findViewById()` fails.\n\n" +
        "There **are** cases where this is intentional. For example, you may have a dedicated " +
        "large tablet layout which adds some extra widgets that are not present in the phone " +
        "version of the layout. As long as the code accessing the layout resource is careful to " +
        "handle this properly, it is valid. In that case, you can suppress this lint check for " +
        "the given extra or missing views, or the whole layout.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, Map<String, File>> layoutFiles;
    private Map<String, Map<String, Set<String>>> layoutIds;
    private Map<String, Map<String, Map<String, Location>>> idLocations;

    @Override
    public void beforeCheckProject(Context context) {
        layoutFiles = new HashMap<>();
        layoutIds = new HashMap<>();
        idLocations = new HashMap<>();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(
            "http://schemas.android.com/apk/res/android", "id");
        if (idAttr == null) {
            return;
        }

        String idValue = idAttr.getValue();
        if (idValue == null || (!idValue.startsWith("@+id/") && !idValue.startsWith("@id/"))) {
            return;
        }

        String fileName = context.file.getName();
        File parent = context.file.getParentFile();
        if (parent == null) {
            return;
        }
        String folderName = parent.getName();

        layoutFiles.computeIfAbsent(fileName, k -> new HashMap<>()).put(folderName, context.file);
        layoutIds.computeIfAbsent(fileName, k -> new HashMap<>())
                 .computeIfAbsent(folderName, k -> new HashSet<>())
                 .add(idValue);
        idLocations.computeIfAbsent(fileName, k -> new HashMap<>())
                   .computeIfAbsent(folderName, k -> new HashMap<>())
                   .put(idValue, context.getLocation(idAttr));
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : layoutIds.entrySet()) {
            String fileName = entry.getKey();
            Map<String, Set<String>> folderIds = entry.getValue();

            if (folderIds.size() <= 1) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderIds.values()) {
                allIds.addAll(ids);
            }

            for (String id : allIds) {
                List<String> presentFolders = new ArrayList<>();
                List<String> missingFolders = new ArrayList<>();

                for (Map.Entry<String, Set<String>> folderEntry : folderIds.entrySet()) {
                    if (folderEntry.getValue().contains(id)) {
                        presentFolders.add(folderEntry.getKey());
                    } else {
                        missingFolders.add(folderEntry.getKey());
                    }
                }

                if (!missingFolders.isEmpty() && !presentFolders.isEmpty()) {
                    String presentFolder = presentFolders.get(0);
                    Location location = idLocations.get(fileName).get(presentFolder).get(id);

                    Location secondary = null;
                    for (int i = missingFolders.size() - 1; i >= 0; i--) {
                        String missing = missingFolders.get(i);
                        File missingFile = layoutFiles.get(fileName).get(missing);
                        if (missingFile != null) {
                            Location loc = Location.create(missingFile);
                            loc.setSecondary(secondary);
                            secondary = loc;
                        }
                    }
                    location.setSecondary(secondary);

                    String message = String.format(
                        "The id `%s` is not defined in all variations of this layout (missing in %s)",
                        id, missingFolders);
                    report(context, ISSUE, location, message);
                }
            }
        }
    }
}