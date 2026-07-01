package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    /** Map from resource name to the location where it was first defined */
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
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Only care about "name" attributes on resource elements
        String attributeName = attribute.getLocalName();
        if (!"name".equals(attributeName)) {
            return;
        }

        // Skip items that are inside <declare-styleable> unless they are the declare-styleable itself
        // We mainly want to track top-level resource definitions
        Element parent = (Element) element.getParentNode();
        if (parent != null && !"resources".equals(parent.getTagName())) {
            // This is a nested element (e.g. <item> inside <declare-styleable>), skip it
            return;
        }

        String tagName = element.getTagName();
        // Skip the root <resources> element itself
        if ("resources".equals(tagName)) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Create a key combining the resource type and name to distinguish
        // between resources of different types with the same name
        String key = tagName + ":" + name;

        // For <item> elements, use the type attribute if available
        if ("item".equals(tagName)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                key = type + ":" + name;
            }
        }

        Location location = context.getLocation(attribute);

        if (mNames.containsKey(key)) {
            Location previousLocation = mNames.get(key);
            String message = String.format(
                    "`%1$s` has already been defined in this folder", name);
            Location secondaryLocation = previousLocation;
            if (secondaryLocation != null) {
                secondaryLocation.setMessage("Previously defined here");
            }
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    message,
                    null);
        } else {
            mNames.put(key, location);
        }
    }
}