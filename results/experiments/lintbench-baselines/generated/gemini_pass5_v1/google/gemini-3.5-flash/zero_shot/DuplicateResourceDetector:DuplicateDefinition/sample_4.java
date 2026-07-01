package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "Defining the same resource more than once in the same resource folder " +
                    "is likely an error, for example attempting to add a new resource " +
                    "without realizing that the name is already used.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, Location> mDefinedResources = new HashMap<>();
    private File mCurrentFolder = null;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("resources");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"resources".equals(element.getTagName())) {
            return;
        }

        File folder = context.file.getParentFile();
        if (mCurrentFolder == null || !mCurrentFolder.equals(folder)) {
            mDefinedResources.clear();
            mCurrentFolder = folder;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String name = childElement.getAttribute("name");
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }

                String tagName = childElement.getTagName();
                if ("public".equals(tagName) || "skip".equals(tagName) || "eat-comment".equals(tagName)) {
                    continue;
                }

                String type = tagName;
                if ("item".equals(tagName)) {
                    String typeAttr = childElement.getAttribute("type");
                    if (typeAttr != null && !typeAttr.trim().isEmpty()) {
                        type = typeAttr;
                    }
                }

                String key = type + "/" + name;
                if (mDefinedResources.containsKey(key)) {
                    Location previousLocation = mDefinedResources.get(key);
                    Location currentLocation = context.getLocation(childElement);
                    
                    if (previousLocation != null) {
                        previousLocation.setMessage("First definition here");
                        currentLocation.setSecondary(previousLocation);
                    }

                    context.report(
                            ISSUE,
                            childElement,
                            currentLocation,
                            String.format("The resource `@%s/%s` has already been defined in this folder", type, name)
                    );
                } else {
                    mDefinedResources.put(key, context.getLocation(childElement));
                }
            }
        }
    }
}