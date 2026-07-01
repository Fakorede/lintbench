public class ResourceCycleDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(...);

    private Map<ResourceRef, List<ResourceRef>> mReferences;
    private Map<ResourceRef, Location> mLocations;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mReferences = Maps.newHashMap();
        mLocations = Maps.newHashMap();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        checkCycles(context);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }
        ResourceRef owner;
        if (folderType == ResourceFolderType.VALUES) {
            Node parent = element.getParentNode();
            if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE
                    || !parent.getNodeName().equals(TAG_RESOURCES)) {
                return;
            }
            owner = getResourceRef(element);
            if (owner == null) {
                return;
            }
            mLocations.put(owner, context.getLocation(element));
        } else {
            if (element.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
                return;
            }
            ResourceType type = ResourceType.getEnum(folderType.getName());
            if (type == null) {
                return;
            }
            String name = Lint.getBaseName(context.file.getName());
            owner = new ResourceRef(type, name);
            mLocations.put(owner, context.getLocation(element));
        }

        // Now collect references from this element
        if (owner.type == ResourceType.STYLE && element.getTagName().equals(TAG_STYLE)) {
            String parent = element.getAttribute(ATTR_PARENT);
            if (parent.isEmpty()) {
                String name = owner.name;
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    addEdge(owner, new ResourceRef(ResourceType.STYLE, name.substring(0, dot)));
                }
            } else if (!parent.startsWith("@") && !parent.startsWith("?")) {
                addEdge(owner, new ResourceRef(ResourceType.STYLE, parent));
            }
        }

        collectReferences(context, element, owner);
    }