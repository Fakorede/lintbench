package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; "
                            + "that's how string translations are done, for example. However, "
                            + "defining the same resource more than once in the same resource "
                            + "folder is likely an error, for example attempting to add a new "
                            + "resource without realizing that the name is already used, and so "
                            + "on.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Map<String, Location>> mLocations;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        if (mLocations == null) {
            mLocations = new HashMap<>();
        }
        String folder = context.file.getParent();
        if (!mLocations.containsKey(folder)) {
            mLocations.put(folder, new HashMap<>());
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        Element parent =
                (element.getParentNode() instanceof Element)
                        ? (Element) element.getParentNode()
                        : null;
        if (parent == null || !TAG_RESOURCES.equals(parent.getTagName())) {
            return;
        }

        ResourceType type = getResourceType(element);
        if (type == null) {
            return;
        }

        String name = attribute.getValue();
        if (name.isEmpty()) {
            return;
        }

        String folder = context.file.getParent();
        Map<String, Location> map = mLocations.get(folder);
        if (map == null) {
            return;
        }

        String key = type.getName() + "/" + name;
        Location previous = map.get(key);
        if (previous == null) {
            map.put(key, context.getLocation(attribute));
        } else {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Duplicate definition of resource "
                            + type.getName()
                            + "/"
                            + name
                            + "; previously defined in "
                            + previous.getFile());
        }
    }

    @Nullable
    private static ResourceType getResourceType(@NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_ITEM.equals(tag)) {
            String typeAttr = element.getAttribute(ATTR_TYPE);
            if (typeAttr.isEmpty()) {
                return null;
            }
            return ResourceType.fromXmlTagName(typeAttr);
        }
        return ResourceType.fromXmlTagName(tag);
    }
}