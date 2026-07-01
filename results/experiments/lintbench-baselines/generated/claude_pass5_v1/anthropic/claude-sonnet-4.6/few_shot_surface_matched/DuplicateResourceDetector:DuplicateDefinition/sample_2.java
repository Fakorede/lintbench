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

    private Map<String, Attr> mNames;

    public DuplicateResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mNames = new HashMap<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // Skip the root <resources> element itself
        if (tagName.equals("resources")) {
            return;
        }

        // Skip elements that are children of a non-resources element (e.g. nested elements
        // like <item> inside <style> or <plurals>)
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }
        String parentTag = parent.getTagName();
        if (!parentTag.equals("resources")) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Build a key that combines the tag name and the resource name to distinguish
        // between different resource types (e.g. <string name="foo"> vs <color name="foo">)
        String type = tagName;
        // For <item> elements, the type attribute determines the resource type
        if (tagName.equals("item")) {
            String typeAttr = element.getAttribute("type");
            if (typeAttr != null && !typeAttr.isEmpty()) {
                type = typeAttr;
            }
        }

        String key = type + ":" + name;

        if (mNames.containsKey(key)) {
            Attr previousAttr = mNames.get(key);
            String message = String.format(
                    "Duplicate resource definition: `%1$s` defined in `%2$s` and here",
                    name,
                    previousAttr.getOwnerElement().getTagName());
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    message);
        } else {
            mNames.put(key, attribute);
        }
    }
}