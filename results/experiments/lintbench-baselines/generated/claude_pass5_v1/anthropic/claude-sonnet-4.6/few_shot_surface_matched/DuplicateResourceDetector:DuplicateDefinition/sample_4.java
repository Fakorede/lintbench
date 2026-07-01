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

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_ITEM = "item";

    /** Map from resource key (type+name) to the location string where it was first defined */
    private Map<String, Attr> mDefined;

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
        mDefined = new HashMap<>();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTR_NAME.equals(attribute.getLocalName()) && !ATTR_NAME.equals(attribute.getName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // Determine the resource type
        String type;
        if (TAG_ITEM.equals(tagName)) {
            // <item type="..." name="...">
            type = element.getAttribute(ATTR_TYPE);
            if (type == null || type.isEmpty()) {
                return;
            }
        } else {
            // e.g. <string name="...">, <dimen name="...">, <color name="...">, etc.
            type = tagName;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        String key = type + "/" + name;

        if (mDefined.containsKey(key)) {
            Attr previous = mDefined.get(key);
            String message = String.format(
                    "`%1$s` has already been defined in this folder",
                    name);
            com.android.tools.lint.detector.api.Location location =
                    context.getLocation(attribute);
            com.android.tools.lint.detector.api.Location secondaryLocation =
                    context.getLocation(previous);
            secondaryLocation.setMessage("Previously defined here");
            location.setSecondary(secondaryLocation);
            context.report(ISSUE, attribute, location, message);
        } else {
            mDefined.put(key, attribute);
        }
    }
}