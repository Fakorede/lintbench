package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UElement;

public class LayoutConsistencyDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InconsistentLayout",
                    "Inconsistent Layouts",
                    "This check ensures that a layout resource which is defined in multiple "
                            + "resource folders, specifies the same set of widgets.\n\n"
                            + "This finds cases where you have accidentally forgotten to add "
                            + "a widget to all variations of the layout, which could result "
                            + "in a runtime crash for some resource configurations when a "
                            + "`findViewById()` fails.\n\n"
                            + "There **are** cases where this is intentional. For example, you "
                            + "may have a dedicated large tablet layout which adds some extra "
                            + "widgets that are not present in the phone version of the layout. "
                            + "As long as the code accessing the layout resource is careful to "
                            + "handle this properly, it is valid. In that case, you can suppress "
                            + "this lint check for the given extra or missing views, or the whole "
                            + "layout.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            LayoutConsistencyDetector.class,
                            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    private final java.util.Map<String, java.util.Map<String, java.util.Set<String>>> mLayoutToConfigs =
            new java.util.HashMap<>();

    private final java.util.Map<String, java.util.Map<String, Location>> mLayoutLocations =
            new java.util.HashMap<>();

    private final java.util.Set<String> mReferencedLayouts = new java.util.HashSet<>();

    @Override
    public boolean appliesTo(@NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        String fileName = context.file.getName();
        int dot = fileName.lastIndexOf('.');
        String layoutName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String folderName = context.file.getParentFile().getName();

        java.util.Set<String> ids = new java.util.HashSet<>();
        collectIds(document.getDocumentElement(), ids);

        synchronized (mLayoutToConfigs) {
            java.util.Map<String, java.util.Set<String>> configs = mLayoutToConfigs.get(layoutName);
            if (configs == null) {
                configs = new java.util.HashMap<>();
                mLayoutToConfigs.put(layoutName, configs);
            }
            configs.put(folderName, ids);

            java.util.Map<String, Location> locations = mLayoutLocations.get(layoutName);
            if (locations == null) {
                locations = new java.util.HashMap<>();
                mLayoutLocations.put(layoutName, locations);
            }
            locations.put(folderName, context.getLocation(document));
        }
    }

    private void collectIds(org.w3c.dom.Element element, java.util.Set<String> ids) {
        if (element == null) return;
        String id = element.getAttributeNS("http://schemas.android.com/apk/res/android", "id");
        if (id != null && !id.isEmpty()) {
            int index = id.indexOf('/');
            if (index >= 0) {
                id = id.substring(index + 1);
            }
            ids.add(id);
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element) {
                collectIds((org.w3c.dom.Element) child, ids);
            }
        }
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull com.android.resources.ResourceType type,
            @NonNull String name,
            boolean isFramework) {
        if (type == com.android.resources.ResourceType.LAYOUT) {
            synchronized (mReferencedLayouts) {
                mReferencedLayouts.add(name);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (java.util.Map.Entry<String, java.util.Map<String, java.util.Set<String>>> entry :
                mLayoutToConfigs.entrySet()) {
            String layoutName = entry.getKey();

            if (!mReferencedLayouts.isEmpty() && !mReferencedLayouts.contains(layoutName)) {
                continue;
            }

            java.util.Map<String, java.util.Set<String>> configs = entry.getValue();
            if (configs.size() <= 1) {
                continue;
            }

            java.util.Set<String> allIds = new java.util.HashSet<>();
            for (java.util.Set<String> ids : configs.values()) {
                allIds.addAll(ids);
            }

            for (java.util.Map.Entry<String, java.util.Set<String>> configEntry : configs.entrySet()) {
                String folderName = configEntry.getKey();
                java.util.Set<String> ids = configEntry.getValue();

                java.util.Set<String> missing = new java.util.HashSet<>(allIds);
                missing.removeAll(ids);

                if (!missing.isEmpty()) {
                    java.util.Map<String, Location> locations = mLayoutLocations.get(layoutName);
                    Location location = locations != null ? locations.get(folderName) : null;
                    if (location != null) {
                        String message =
                                String.format(
                                        "Layout `%s` in `%s` is missing the following IDs defined in other configurations: %s",
                                        layoutName, folderName, missing.toString());
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }
}