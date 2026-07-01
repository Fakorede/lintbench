package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
        // We only care about "name" attributes
        if (!"name".equals(attribute.getLocalName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Only consider direct children of the root <resources> element
        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String tagName = element.getTagName();

        // Skip <item> elements that don't have a type attribute (they're not standalone resources)
        // or handle them with their type
        String resourceType;
        if ("item".equals(tagName)) {
            resourceType = element.getAttribute("type");
            if (resourceType == null || resourceType.isEmpty()) {
                return;
            }
        } else {
            resourceType = tagName;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Create a unique key combining the resource type and name
        String key = resourceType + "/" + name;

        Location existingLocation = mNames.get(key);
        if (existingLocation != null) {
            // We have a duplicate
            Location location = context.getLocation(attribute);
            location.setSecondary(existingLocation);
            existingLocation.setMessage("Previously defined here");

            context.report(
                    ISSUE,
                    attribute,
                    location,
                    String.format(
                            "`%1$s` has already been defined in this folder",
                            name));
        } else {
            mNames.put(key, context.getLocation(attribute));
        }
    }
}