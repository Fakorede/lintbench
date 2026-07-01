public class VectorDetector extends ResourceXmlDetector {
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_PATH = "path";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final int MAX_DIMENSION_DP = 200;

    private static final Set<String> UNSUPPORTED_ATTRIBUTES = Collections.singleton(ATTR_FILL_TYPE);

    public static final Issue ISSUE = Issue.create(
        "VectorRaster",
        "Vector Image Generation",
        "...",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, TAG_CLIP_PATH, TAG_PATH);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }
        if (!context.getMainProject().isGradleProject()) {
            return;
        }
        String tag = element.getTagName();
        if (TAG_VECTOR.equals(tag)) {
            if (context.getMainProject().getMinSdk() < 21) {
                checkLargeIcon(context, element);
            }
        } else if (TAG_CLIP_PATH.equals(tag)) {
            if (context.getMainProject().getMinSdk() < 24) {
                context.report(ISSUE, context.getLocation(element), "...");
            }
        } else if (TAG_PATH.equals(tag)) {
            if (context.getMainProject().getMinSdk() < 24) {
                NamedNodeMap attributes = element.getAttributes();
                for (int i=0; i<attributes.getLength(); i++) {
                    Attr attr = (Attr) attributes.item(i);
                    String name = attr.getLocalName();
                    if (name == null) name = attr.getName();
                    if (ATTR_FILL_TYPE.equals(name)) {
                        context.report(ISSUE, context.getLocation(attr), "...");
                    }
                }
            }
        }
    }
    ...
}