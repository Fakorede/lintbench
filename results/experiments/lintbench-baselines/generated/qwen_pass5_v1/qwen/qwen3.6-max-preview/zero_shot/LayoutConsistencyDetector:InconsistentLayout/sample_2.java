package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import java.io.File;
import java.util.*;

public class LayoutConsistencyDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ID = "id";

    private final Map<String, Map<String, Set<String>>> layoutConfigIds = new HashMap<>();
    private final Map<String, Map<String, Map<String, Location>>> layoutConfigIdLocations = new HashMap<>();
    private final Map<String, Map<String, Location>> layoutConfigFileLocations = new HashMap<>();

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
            "this lint check for the given extra or missing views, or the whole layout",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(LayoutConsistencyDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String idAttr = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        String idName = extractIdName(idAttr);
        if (idName == null || idName.isEmpty()) {
            return;
        }

        File file = context.file;
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String fileName = file.getName();
        String configName = parent.getName();

        layoutConfigIds.computeIfAbsent(fileName, k -> new HashMap<>())
                .computeIfAbsent(configName, k -> new HashSet<>())
                .add(idName);

        layoutConfigIdLocations.computeIfAbsent(fileName, k -> new HashMap<>())
                .computeIfAbsent(configName, k -> new HashMap<>())
                .put(idName, context.getLocation(element));

        layoutConfigFileLocations.computeIfAbsent(fileName, k -> new HashMap<>())
                .putIfAbsent(configName, context.getLocation(element));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Map<String, Set<String>>> layoutEntry : layoutConfigIds.entrySet()) {
            String fileName = layoutEntry.getKey();
            Map<String, Set<String>> configToIds = layoutEntry.getValue();

            if (configToIds.size() < 2) {
                continue;
            }

            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : configToIds.values()) {
                allIds.addAll(ids);
            }

            for (Map.Entry<String, Set<String>> configEntry : configToIds.entrySet()) {
                String configName = configEntry.getKey();
                Set<String> configIds = configEntry.getValue();

                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(configIds);

                if (missingIds.isEmpty()) {
                    continue;
                }

                Location fileLocation = layoutConfigFileLocations.get(fileName).get(configName);
                if (fileLocation == null) {
                    continue;
                }

                for (String missingId : missingIds) {
                    String definedConfig = null;
                    Location definedLocation = null;

                    for (Map.Entry<String, Set<String>> otherConfig : configToIds.entrySet()) {
                        if (otherConfig.getValue().contains(missingId)) {
                            definedConfig = otherConfig.getKey();
                            Map<String, Location> idLocs = layoutConfigIdLocations.get(fileName).get(definedConfig);
                            if (idLocs != null) {
                                definedLocation = idLocs.get(missingId);
                            }
                            break;
                        }
                    }

                    Location location = Location.create(
                            fileLocation.getFile(),
                            fileLocation.getStart(),
                            fileLocation.getEnd()
                    );
                    if (definedLocation != null) {
                        location.setSecondary(definedLocation);
                    }

                    String message = String.format(
                            "The id `@+id/%s` is defined in `%s` but missing in `%s`",
                            missingId, definedConfig, configName);

                    context.report(ISSUE, location, message);
                }
            }
        }

        layoutConfigIds.clear();
        layoutConfigIdLocations.clear();
        layoutConfigFileLocations.clear();
    }

    private static String extractIdName(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        }
        if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }
}