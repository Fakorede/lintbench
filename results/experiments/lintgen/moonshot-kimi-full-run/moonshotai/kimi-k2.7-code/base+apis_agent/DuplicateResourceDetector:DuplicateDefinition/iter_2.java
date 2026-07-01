@Override
public Collection<String> getApplicableElements() { return Collections.singletonList(TAG_RESOURCES); }

@Override
public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
    if (!TAG_RESOURCES.equals(element.getTagName())) return;
    File folder = context.file.getParentFile();
    if (folder == null) return;
    String folderName = folder.getName();
    String base = getBaseFolderName(folderName);
    if (!"values".equals(base)) return;
    String folderPath = folder.getPath();
    NodeList children = element.getChildNodes();
    for (int i=0; i<children.getLength(); i++) {
        Node node = children.item(i);
        if (node.getNodeType() != Node.ELEMENT_NODE) continue;
        Element child = (Element) node;
        String name = child.getAttribute(ATTR_NAME);
        if (name.isEmpty()) continue;
        String type = getValueResourceType(child);
        if (type == null || type.isEmpty()) continue;
        record(folderPath, type, name, context.getLocation(child));
    }
}