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
        // We only care about "name" attributes on resource elements
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        // Make sure the parent is the root resources element or a declare-styleable
        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }

        String parentName = parent.getNodeName();
        if (!"resources".equals(parentName) && !"declare-styleable".equals(parentName)) {
            return;
        }

        String tagName = element.getTagName();

        // Skip certain tags that are allowed to have duplicate names (e.g., item inside
        // declare-styleable which are attr references)
        if ("declare-styleable".equals(parentName) && "attr".equals(tagName)) {
            // Attrs inside declare-styleable can reference existing attrs
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Build a key that includes the element type to distinguish between different
        // resource types with the same name (e.g., a string and a color both named "foo"
        // are not duplicates of each other)
        String key = tagName + ":" + name;

        // Handle "item" elements which specify type via a "type" attribute
        if ("item".equals(tagName)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                key = type + ":" + name;
            }
        }

        if (mNames.containsKey(key)) {
            Location location = context.getLocation(attribute);
            Location previousLocation = mNames.get(key);
            if (previousLocation != null) {
                location.setSecondary(previousLocation);
                previousLocation.setMessage("Previously defined here");
            }
            context.report(
                    ISSUE,
                    attribute,
                    location,
                    String.format("`%1$s` has already been defined in this folder", name));
        } else {
            mNames.put(key, context.getLocation(attribute));
        }
    }
}