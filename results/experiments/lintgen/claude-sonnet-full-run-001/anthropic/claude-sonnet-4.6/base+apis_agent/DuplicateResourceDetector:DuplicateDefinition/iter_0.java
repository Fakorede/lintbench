package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFixer;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;

/**
 * Checks for duplicate resource definitions within the same resource folder.
 */
public class DuplicateResourceDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definition",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining " +
            "the same resource more than once in the same resource folder is likely " +
            "an error, for example attempting to add a new resource without realizing " +
            "that the name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from resource folder (qualifier string) to a map of resource keys
     * (type+name) to the location where they were first defined.
     */
    private final Map<String, Map<String, Location>> mFolderToResourceMap = new HashMap<>();

    /**
     * Map from resource folder (qualifier string) to a map of resource keys
     * (type+name) to the list of duplicate locations found after the first.
     */
    private final Map<String, Map<String, List<Location>>> mDuplicates = new HashMap<>();

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RESOURCES);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're visiting the root <resources> element; iterate over its children
        // to find resource definitions.
        File folder = context.file.getParentFile();
        String folderKey = folder != null ? folder.getPath() : context.file.getPath();

        Map<String, Location> resourceMap = mFolderToResourceMap.get(folderKey);
        if (resourceMap == null) {
            resourceMap = new HashMap<>();
            mFolderToResourceMap.put(folderKey, resourceMap);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            String tag = item.getTagName();

            // Determine the resource type and name
            String type;
            String name;

            if (TAG_ITEM.equals(tag)) {
                // <item type="..." name="...">
                type = item.getAttribute(ATTR_TYPE);
                name = item.getAttribute(ATTR_NAME);
                if (type == null || type.isEmpty() || name == null || name.isEmpty()) {
                    continue;
                }
            } else if (TAG_STYLE.equals(tag)) {
                // <style name="...">
                type = TAG_STYLE;
                name = item.getAttribute(ATTR_NAME);
                if (name == null || name.isEmpty()) {
                    continue;
                }
            } else {
                // e.g. <string name="...">, <color name="...">, <dimen name="...">, etc.
                type = tag;
                name = item.getAttribute(ATTR_NAME);
                if (name == null || name.isEmpty()) {
                    continue;
                }
            }

            // Normalize the name (replace dots and hyphens with underscores for comparison)
            String normalizedName = name.replace('.', '_').replace('-', '_');
            String resourceKey = type + "/" + normalizedName;

            Location location = context.getLocation(item);

            if (resourceMap.containsKey(resourceKey)) {
                // This is a duplicate — record it
                Map<String, List<Location>> duplicatesForFolder = mDuplicates.get(folderKey);
                if (duplicatesForFolder == null) {
                    duplicatesForFolder = new HashMap<>();
                    mDuplicates.put(folderKey, duplicatesForFolder);
                }
                List<Location> duplicateLocations = duplicatesForFolder.get(resourceKey);
                if (duplicateLocations == null) {
                    duplicateLocations = new ArrayList<>();
                    // Add the original location as the first entry so we can link them
                    duplicateLocations.add(resourceMap.get(resourceKey));
                    duplicatesForFolder.put(resourceKey, duplicateLocations);
                }
                duplicateLocations.add(location);
            } else {
                resourceMap.put(resourceKey, location);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Report all duplicates found
        for (Map.Entry<String, Map<String, List<Location>>> folderEntry : mDuplicates.entrySet()) {
            Map<String, List<Location>> duplicatesForFolder = folderEntry.getValue();
            for (Map.Entry<String, List<Location>> entry : duplicatesForFolder.entrySet()) {
                String resourceKey = entry.getKey();
                List<Location> locations = entry.getValue();

                // locations.get(0) is the original definition
                // locations.get(1..n) are the duplicates
                Location originalLocation = locations.get(0);

                // Extract a friendly resource name from the key (e.g. "string/app_name" -> "app_name")
                String[] parts = resourceKey.split("/", 2);
                String type = parts.length > 1 ? parts[0] : resourceKey;
                String name = parts.length > 1 ? parts[1] : resourceKey;

                for (int i = 1; i < locations.size(); i++) {
                    Location duplicateLocation = locations.get(i);

                    // Link the original location as a secondary location
                    Location secondary = originalLocation.withMessage(
                            "Originally defined here");
                    duplicateLocation.setSecondary(secondary);

                    context.report(
                            ISSUE,
                            duplicateLocation,
                            String.format(
                                    "`%1$s` has already been defined in this folder",
                                    name));
                }
            }
        }

        // Clear state for next project
        mFolderToResourceMap.clear();
        mDuplicates.clear();
    }
}