package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

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

    private java.io.File currentFolder = null;
    private final java.util.Set<String> folderNames = new java.util.HashSet<>();

    @Override
    public java.util.Collection<String> getApplicableAttributes() {
        return java.util.Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        java.io.File parent = context.file.getParentFile();
        if (currentFolder == null || !currentFolder.equals(parent)) {
            currentFolder = parent;
            folderNames.clear();
        }
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element element = attribute.getOwnerElement();
        String type = element.getTagName();
        if ("item".equals(type)) {
            String typeAttr = element.getAttribute("type");
            if (typeAttr != null && !typeAttr.isEmpty()) {
                type = typeAttr;
            }
        }

        String name = attribute.getValue();
        String key = type + "/" + name;

        if (folderNames.contains(key)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format("Duplicate definition of `%s`", key));
        } else {
            folderNames.add(key);
        }
    }
}