package com.android.tools.lint.checks;

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
import org.w3c.dom.Node;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private String mCurrentFolder = null;
    private final Set<String> mDefinedResources = new HashSet<>();

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull XmlContext context) {
        File file = context.file;
        File parentFile = file.getParentFile();
        String folderPath = parentFile != null ? parentFile.getAbsolutePath() : "";
        if (mCurrentFolder == null || !mCurrentFolder.equals(folderPath)) {
            mCurrentFolder = folderPath;
            mDefinedResources.clear();
        }
    }

    @Override
    public void visitAttribute(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        Node parentNode = element.getParentNode();
        if (parentNode == null || !"resources".equals(parentNode.getNodeName())) {
            return;
        }

        String name = attribute.getValue();
        if (name.isEmpty()) {
            return;
        }

        String type = element.getTagName();
        if ("item".equals(type)) {
            type = element.getAttribute("type");
            if (type == null || type.isEmpty()) {
                return;
            }
        }

        String key = type + ":" + name;
        if (mDefinedResources.contains(key)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Resource \"%s\" of type \"%s\" has already been defined in this folder", name, type));
        } else {
            mDefinedResources.add(key);
        }
    }
}