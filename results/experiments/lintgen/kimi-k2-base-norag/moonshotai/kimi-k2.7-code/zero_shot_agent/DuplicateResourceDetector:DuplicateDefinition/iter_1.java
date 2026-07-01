@Override
public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
    ResourceFolderType folderType = context.getResourceFolderType();
    if (folderType == null) return;

    Node parent = element.getParentNode();
    if (parent == null) return;

    if (folderType != ResourceFolderType.VALUES && parent.getNodeType() == Node.DOCUMENT_NODE) {
        // file-based resource root
        File file = context.getFile();
        File folder = file.getParentFile();
        if (folder == null) return;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        String type = folderType.name().toLowerCase(Locale.US);
        add(folder.getAbsolutePath(), type, name, context.getLocation(element));
        return;
    }

    if (folderType != ResourceFolderType.VALUES) {
        return;
    }

    if (parent.getNodeName() == null || !RESOURCES_TAG.equals(parent.getNodeName())) {
        return;
    }

    // process value resource
    ...
}