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
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final String ATTR_NAME = "name";

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

    private final Map<String, Map<String, Location>> mResourceNames = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mResourceNames.clear();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        mResourceNames.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to do here; duplicates are tracked across files in the same resource folder.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (attribute.getNamespaceURI() != null) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        Node parent = owner.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String resourceType = owner.getNodeName();
        String resourceName = attribute.getValue();
        if (resourceName == null || resourceName.isEmpty()) {
            return;
        }

        String folderPath = context.file.getParentFile().getPath();
        String key = folderPath + ":" + resourceType + "/" + resourceName;

        Map<String, Location> folderResources = mResourceNames.get(folderPath);
        if (folderResources == null) {
            folderResources = new HashMap<>();
            mResourceNames.put(folderPath, folderResources);
        }

        Location attributeLocation = context.getValueLocation(attribute);
        Location firstLocation = folderResources.get(key);
        if (firstLocation != null) {
            attributeLocation.setSecondary(firstLocation);
            context.report(
                    ISSUE,
                    attributeLocation,
                    "Duplicate definition of "
                            + resourceType
                            + " resource '"
                            + resourceName
                            + "' (first defined in "
                            + firstLocation.getFile().getName()
                            + ")");
        } else {
            folderResources.put(key, attributeLocation);
        }
    }
}