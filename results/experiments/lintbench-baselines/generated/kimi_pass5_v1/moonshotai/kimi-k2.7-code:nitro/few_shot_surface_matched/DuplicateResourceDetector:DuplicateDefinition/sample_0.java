package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; that's how "
                            + "string translations are done, for example. However, defining the same resource "
                            + "more than once in the same resource folder is likely an error, for example "
                            + "attempting to add a new resource without realizing that the name is already used.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.ALL_RESOURCES_SCOPE));

    private final java.util.Set<String> mResourceNames = new java.util.HashSet<>();

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(com.android.tools.lint.detector.api.XmlContext context) {
    }

    @Override
    public void visitAttribute(com.android.tools.lint.detector.api.XmlContext context,
            org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        org.w3c.dom.Node parent = owner.getParentNode();
        if (parent == null || parent.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }

        org.w3c.dom.Element parentElement = (org.w3c.dom.Element) parent;
        if (!"resources".equals(parentElement.getTagName())) {
            return;
        }

        String name = attribute.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        String tag = owner.getTagName();
        if (tag == null || tag.isEmpty()) {
            return;
        }

        String type = tag;
        if ("item".equals(tag)) {
            org.w3c.dom.Attr typeAttr = owner.getAttributeNode("type");
            if (typeAttr != null) {
                String itemType = typeAttr.getValue();
                if (itemType != null && !itemType.isEmpty()) {
                    type = itemType;
                }
            }
        }

        java.io.File folder = context.file.getParentFile();
        String folderPath = folder != null ? folder.getAbsolutePath() : context.file.getPath();
        String key = folderPath + "/" + type + "/" + name;

        if (!mResourceNames.add(key)) {
            String folderName = folder != null ? folder.getName() : context.file.getName();
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "%1$s/%2$s has already been defined in this folder (%3$s)",
                            type, name, folderName));
        }
    }
}