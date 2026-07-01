package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;

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
     * Map from resource folder path to a map of resource keys (type+name) to
     * the location where they were first defined.
     */
    private final Map<String, Map<String, Location>> mFolderToResourceMap = new HashMap<>();

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
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Process the document to find the <resources> root element
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        String rootTag = root.getTagName();
        if (!TAG_RESOURCES.equals(rootTag)) {
            return;
        }

        File folder = context.file.getParentFile();
        String folderKey = folder != null ? folder.getPath() : context.file.getPath();

        Map<String, Location> resourceMap = mFolderToResourceMap.computeIfAbsent(folderKey, k -> new HashMap<>());

        processElement(context, root, resourceMap, null);
    }

    private void processElement(@NonNull XmlContext context, @NonNull Element element,
            @NonNull Map<String, Location> resourceMap, String parentType) {
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
                if (type == null || type.isEmpty()) {
                    // Could be a style item like <item name="android:textColor">
                    // These are children of <style> elements - skip them for duplicate checking
                    // at the top level, but if we have a parentType of "style", handle differently
                    if (name != null && !name.isEmpty() && parentType != null) {
                        // Style item - check for duplicates within this style
                        // We use a compound key including the parent element's name
                        String parentName = element.getAttribute(ATTR_NAME);
                        if (parentName != null && !parentName.isEmpty()) {
                            String resourceKey = parentType + "/" + parentName + "/" + name;
                            checkAndReport(context, item, name, resourceKey, resourceMap);
                        }
                    }
                    continue;
                }
                if (name == null || name.isEmpty()) {
                    continue;
                }
            } else {
                // e.g. <string name="...">, <color name="...">, <dimen name="...">,
                // <style name="...">, etc.
                type = tag;
                name = item.getAttribute(ATTR_NAME);
                if (name == null || name.isEmpty()) {
                    continue;
                }
            }

            // Normalize the name (replace dots and hyphens with underscores for comparison)
            String normalizedName = name.replace('.', '_').replace('-', '_');
            String resourceKey = type + "/" + normalizedName;

            checkAndReport(context, item, name, resourceKey, resourceMap);

            // For style/declare-styleable elements, also check their children for duplicate items
            if ("style".equals(type) || "declare-styleable".equals(type)) {
                // Create a sub-map for items within this style
                String styleKey = type + "/" + normalizedName + "/";
                Map<String, Location> styleItemMap = new HashMap<>();
                processStyleItems(context, item, styleItemMap, styleKey);
            }
        }
    }

    private void processStyleItems(@NonNull XmlContext context, @NonNull Element styleElement,
            @NonNull Map<String, Location> itemMap, @NonNull String prefix) {
        NodeList children = styleElement.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            String tag = item.getTagName();
            if (!TAG_ITEM.equals(tag)) {
                continue;
            }
            String name = item.getAttribute(ATTR_NAME);
            if (name == null || name.isEmpty()) {
                continue;
            }
            String resourceKey = prefix + name;
            checkAndReport(context, item, name, resourceKey, itemMap);
        }
    }

    private void checkAndReport(@NonNull XmlContext context, @NonNull Element item,
            @NonNull String name, @NonNull String resourceKey,
            @NonNull Map<String, Location> resourceMap) {
        Location location = context.getLocation(item);

        if (resourceMap.containsKey(resourceKey)) {
            Location previousLocation = resourceMap.get(resourceKey);

            // Create a secondary location pointing to the original definition
            Location secondary = Location.create(
                    previousLocation.getFile(),
                    previousLocation.getStart(),
                    previousLocation.getEnd());
            secondary.setMessage("Previously defined here");
            location.setSecondary(secondary);

            context.report(
                    ISSUE,
                    item,
                    location,
                    String.format(
                            "`%1$s` has already been defined in this folder",
                            name));
        } else {
            resourceMap.put(resourceKey, location);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handled in visitDocument
    }
}