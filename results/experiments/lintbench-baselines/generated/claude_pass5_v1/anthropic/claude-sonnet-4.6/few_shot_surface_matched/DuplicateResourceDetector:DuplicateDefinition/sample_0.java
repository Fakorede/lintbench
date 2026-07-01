package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate resource definition",
                    "You can define a resource multiple times in different resource folders; "
                            + "that's how string translations are done, for example. However, "
                            + "defining the same resource more than once in the same resource "
                            + "folder is likely an error, for example attempting to add a new "
                            + "resource without realizing that the name is already used, and "
                            + "so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    /** Map from resource key (type+name) to the attribute where it was first defined */
    private final Map<String, Attr> mDefinedResources = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mDefinedResources.clear();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // Skip the root <resources> element itself
        if (tagName.equals("resources")) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Determine the resource type from the element tag or from the "type" attribute
        // (used in <item type="..."> elements)
        String resourceType;
        if (tagName.equals("item")) {
            resourceType = element.getAttribute(ATTR_TYPE);
            if (resourceType == null || resourceType.isEmpty()) {
                return;
            }
        } else {
            resourceType = tagName;
        }

        // Build a unique key for this resource definition
        String key = resourceType + "/" + name;

        Attr existing = mDefinedResources.get(key);
        if (existing != null) {
            String message =
                    String.format(
                            "`%1$s` has already been defined in this folder",
                            name);
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    message);
        } else {
            mDefinedResources.put(key, attribute);
        }
    }
}