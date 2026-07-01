package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
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
                            + "resource without realizing that the name is already used. Delete "
                            + "or rename the duplicate definition.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Map<String, Map<String, Map<String, Location>>> mResourceLocations;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mResourceLocations = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mResourceLocations == null) {
            mResourceLocations = new HashMap<>();
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String typeName = getResourceTypeName(element);
        if (typeName == null) {
            return;
        }

        String folderPath = context.file.getParent();
        if (folderPath == null) {
            return;
        }

        Map<String, Map<String, Location>> byType = mResourceLocations.get(folderPath);
        if (byType == null) {
            byType = new HashMap<>();
            mResourceLocations.put(folderPath, byType);
        }

        Map<String, Location> byName = byType.get(typeName);
        if (byName == null) {
            byName = new HashMap<>();
            byType.put(typeName, byName);
        }

        Location previous = byName.get(name);
        if (previous != null) {
            Location current = context.getLocation(attribute);
            Location location = current.withSecondary(previous, "Previously defined here");
            context.report(
                    ISSUE,
                    location,
                    String.format(
                            "Duplicate definition of %1$s resource named `%2$s` in this resource folder",
                            typeName, name));
        } else {
            byName.put(name, context.getLocation(attribute));
        }
    }

    private static String getResourceTypeName(@NonNull Element element) {
        String tag = element.getTagName();

        if ("item".equals(tag)) {
            Attr typeAttr = element.getAttributeNode("type");
            return typeAttr != null ? typeAttr.getValue() : null;
        }

        if ("string-array".equals(tag) || "integer-array".equals(tag)) {
            return "array";
        }

        if ("declare-styleable".equals(tag)) {
            return "styleable";
        }

        if ("enum".equals(tag) || "flag".equals(tag) || "public".equals(tag)) {
            return null;
        }

        return tag;
    }
}