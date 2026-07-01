package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
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

    /** Map from resource name to the location (as a string) where it was first defined */
    private Map<String, Attr> mNames;

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
        if (element == null) {
            return;
        }

        // Only look at resource definition elements (direct children of <resources>)
        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String tagName = element.getTagName();
        // Skip "item" elements that are not resource items (e.g. style items)
        // Only top-level resource items should be checked
        if ("item".equals(tagName)) {
            // Make sure this is a resource item with a type attribute
            String type = element.getAttribute("type");
            if (type == null || type.isEmpty()) {
                return;
            }
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Build a key that includes the tag/type to distinguish between
        // resources of different types with the same name
        String type = getResourceType(element);
        if (type == null) {
            return;
        }

        String key = type + ":" + name;

        if (mNames.containsKey(key)) {
            Attr firstAttr = mNames.get(key);
            String message =
                    String.format(
                            "`%1$s` has already been defined in this folder",
                            name);
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    message,
                    null);
        } else {
            mNames.put(key, attribute);
        }
    }

    /**
     * Returns the resource type string for the given element, or null if it cannot be determined.
     */
    private static String getResourceType(@NonNull Element element) {
        String tagName = element.getTagName();
        if ("item".equals(tagName)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                return type;
            }
            return null;
        }
        // For standard resource tags like <string>, <color>, <dimen>, <bool>, <integer>,
        // <style>, <declare-styleable>, <attr>, <plurals>, <array>, <string-array>,
        // <integer-array>, <drawable>, etc.
        return tagName;
    }
}