public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {
    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";

    private List<AttrInfo> mAttrs;
    private Map<String, Set<String>> mMethods;

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "...",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@")) {
            return;
        }
        if (mAttrs == null) {
            mAttrs = new ArrayList<>();
        }
        String contextClass = getContextClass(context, attribute);
        mAttrs.add(new AttrInfo(context, attribute, value, contextClass));
    }

    private String getContextClass(@NonNull XmlContext context, @NonNull Attr attribute) {
        Node node = attribute.getOwnerElement();
        while (node != null && node.getNodeType() != Node.DOCUMENT_NODE) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String ctx = element.getAttributeNS(TOOLS_URI, "context");
                if (ctx != null && !ctx.isEmpty()) {
                    if (ctx.startsWith(".")) {
                        String pkg = context.getMainProject().getPackage();
                        if (pkg != null && !pkg.isEmpty()) {
                            ctx = pkg + ctx;
                        }
                    }
                    return ctx;
                }
            }
            node = node.getParentNode();
        }
        return null;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                if (isValidOnClickHandler(node)) {
                    PsiClass containingClass = node.getContainingClass();
                    if (containingClass != null) {
                        String className = containingClass.getQualifiedName();
                        if (className != null) {
                            if (mMethods == null) {
                                mMethods = new HashMap<>();
                            }
                            mMethods.computeIfAbsent(className, k -> new HashSet<>()).add(node.getName());
                        }
                    }
                }
            }
        };
    }

    private static boolean isValidOnClickHandler(@NonNull UMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.VOID.equals(returnType)) {
            return false;
        }
        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 1) {
            return false;
        }
        PsiType type = parameters.get(0).getType();
        return type != null && type.equalsToText("android.view.View");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mAttrs == null) {
            return;
        }
        for (AttrInfo info : mAttrs) {
            if (!methodExists(info.name, info.contextClass)) {
                info.context.report(ISSUE, info.attribute, info.context.getValueLocation(info.attribute),
                        "Corresponding method handler '" + info.name + "' not found");
            }
        }
    }

    private boolean methodExists(@NonNull String name, String contextClass) {
        if (contextClass != null) {
            Set<String> visited = new HashSet<>();
            String current = contextClass;
            while (current != null && visited.add(current)) {
                Set<String> methods = mMethods != null ? mMethods.get(current) : null;
                if (methods != null && methods.contains(name)) {
                    return true;
                }
                current = getSuperClass(current);
            }
            return false;
        }
        if (mMethods != null) {
            for (Set<String> methods : mMethods.values()) {
                if (methods.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getSuperClass(String className) {
        // Need a map from class to superclass
    }
}