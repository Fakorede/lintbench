package com.android.tools.lint.checks;

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

import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining the same " +
            "resource more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_RESOURCES = "resources";
    private static final String ATTR_NAME = "name";

    private Map<String, Location> mNames;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mNames = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mNames = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element && TAG_RESOURCES.equals(((Element) parent).getTagName())) {
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                Location location = context.getLocation(element);
                Location prev = mNames.put(name, location);
                if (prev != null) {
                    String message = String.format("Duplicate definition of resource `%s`", name);
                    location.setSecondary(prev);
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}