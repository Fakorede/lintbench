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
                    "Duplicate definitions of resources",
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

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_ITEM = "item";
    private static final String TAG_RESOURCES = "resources";

    /** Map from resource key (type+name) to the attribute where it was first defined */
    private Map<String, Attr> mNames;

    public DuplicateResourceDetector() {
    }

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
        mNames = new HashMap<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        Element parent = (Element) element.getParentNode();

        // Only care about direct children of <resources>
        if (parent == null || !TAG_RESOURCES.equals(parent.getTagName())) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Determine the resource type
        String tag = element.getTagName();
        String type;
        if (TAG_ITEM.equals(tag)) {
            type = element.getAttribute(ATTR_TYPE);
            if (type == null || type.isEmpty()) {
                return;
            }
        } else {
            type = tag;
        }

        String key = type + "/" + name;

        if (mNames.containsKey(key)) {
            Attr previousAttr = mNames.get(key);
            String message = String.format(
                    "Duplicate resource definition for `%s` already defined here: %s",
                    name,
                    context.getLocation(previousAttr).toString());
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