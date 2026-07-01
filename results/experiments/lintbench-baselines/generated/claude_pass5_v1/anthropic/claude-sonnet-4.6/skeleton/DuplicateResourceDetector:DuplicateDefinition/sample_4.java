package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; "
                            + "that's how string translations are done, for example. However, "
                            + "defining the same resource more than once in the same resource "
                            + "folder is likely an error, for example attempting to add a new "
                            + "resource without realizing that the name is already used, and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Map from resource name to the location where it was first defined in the current file */
    private Map<String, Location> mNames;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mNames = new HashMap<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"name".equals(attribute.getLocalName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // Only consider resource-defining elements (not sub-elements like <item> inside <style>)
        // We check that the parent is the root <resources> element
        if (element.getParentNode() == null) {
            return;
        }

        String parentTagName = element.getParentNode().getNodeName();
        if (!"resources".equals(parentTagName)) {
            // This is a nested element (e.g., an item inside a style), skip it
            return;
        }

        // Build a key from the tag name and the resource name to distinguish
        // e.g. <string name="foo"> from <color name="foo">
        String resourceName = attribute.getValue();
        if (resourceName == null || resourceName.isEmpty()) {
            return;
        }

        // Normalize: use tag name + name as the key
        // For <item> elements, also consider the type attribute
        String key;
        if ("item".equals(tagName)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                key = type + ":" + resourceName;
            } else {
                key = tagName + ":" + resourceName;
            }
        } else {
            key = tagName + ":" + resourceName;
        }

        Location location = context.getLocation(attribute);

        if (mNames.containsKey(key)) {
            Location previousLocation = mNames.get(key);
            String message =
                    String.format(
                            "`%1$s` has already been defined in this folder",
                            resourceName);
            Location secondaryLocation = previousLocation.withMessage("Previously defined here");
            location.setSecondary(secondaryLocation);
            context.report(ISSUE, attribute, location, message);
        } else {
            mNames.put(key, location);
        }
    }
}