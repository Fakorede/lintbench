package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
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
                    "You can define a resource multiple times in different resource folders; that's"
                            + " how string translations are done, for example. However, defining"
                            + " the same resource more than once in the same resource folder is"
                            + " likely an error, for example attempting to add a new resource"
                            + " without realizing that the name is already used, and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private final Map<String, Map<String, Location>> mFirstLocations = new HashMap<>();
    private String mCurrentFolder;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public void beforeCheckProject(Context context) {
        mFirstLocations.clear();
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        File parent = context.file.getParentFile();
        mCurrentFolder = parent != null ? parent.getPath() : context.file.getPath();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !namespace.isEmpty()) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tag = element.getTagName();
        if ("enum".equals(tag) || "flag".equals(tag)) {
            return;
        }

        String type;
        if (TAG_ITEM.equals(tag)) {
            type = element.getAttribute(ATTR_TYPE);
            if (type.isEmpty()) {
                return;
            }
        } else if (TAG_ARRAY.equals(tag)
                || TAG_STRING_ARRAY.equals(tag)
                || TAG_INTEGER_ARRAY.equals(tag)) {
            type = "array";
        } else {
            type = tag;
        }

        String name = attribute.getValue().trim();
        if (name.isEmpty()) {
            return;
        }

        Map<String, Location> folderMap = mFirstLocations.get(mCurrentFolder);
        if (folderMap == null) {
            folderMap = new HashMap<>();
            mFirstLocations.put(mCurrentFolder, folderMap);
        }

        String key = type + "/" + name;
        Location first = folderMap.get(key);
        Location current = context.getLocation(attribute);
        if (first != null) {
            String message =
                    String.format(
                            "Duplicate definition of resource `%1$s/%2$s` in this resource folder",
                            type, name);
            Location location = Location.create(context.file, current.getStart(), current.getEnd());
            location.setSecondary(first);
            context.report(ISSUE, attribute, location, message);
        } else {
            folderMap.put(key, current);
        }
    }
}