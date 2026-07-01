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
                            + "resource without realizing that the name is already used, and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

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

        // Skip elements that are not top-level resource definitions
        // (e.g., <item> inside <style> or <declare-styleable>)
        if (element.getParentNode() == null) {
            return;
        }
        org.w3c.dom.Node parentNode = element.getParentNode();
        String parentTag = parentNode.getNodeName();

        // Only process direct children of <resources>
        if (!"resources".equals(parentTag)) {
            return;
        }

        // Skip tags that are not resource definitions
        switch (tagName) {
            case "eat-comment":
            case "skip":
            case "java-symbol":
                return;
            default:
                break;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Build a key that includes the tag name to distinguish between
        // different resource types (e.g., <string name="foo"> vs <color name="foo">)
        // However, some resource types share a namespace (e.g., <item type="string">)
        String type = tagName;
        if ("item".equals(tagName)) {
            String typeAttr = element.getAttribute("type");
            if (typeAttr != null && !typeAttr.isEmpty()) {
                type = typeAttr;
            }
        }

        String key = type + ":" + name;

        if (mNames.containsKey(key)) {
            Attr previous = mNames.get(key);
            String message = String.format(
                    "Duplicate resource definition `%1$s` for type `%2$s`",
                    name, type);
            com.android.tools.lint.detector.api.Location location =
                    context.getLocation(attribute);
            com.android.tools.lint.detector.api.Location previousLocation =
                    context.getLocation(previous);
            location.setSecondary(previousLocation);
            context.report(ISSUE, attribute, location, message);
        } else {
            mNames.put(key, attribute);
        }
    }
}