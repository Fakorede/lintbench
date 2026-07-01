public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.JavaScanner {
    public static final Issue ISSUE = Issue.create(..., new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String FQCN_STRING = "java.lang.String";
    private static final String FQCN_BUNDLE = "android.os.Bundle";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    private final Map<String, Element> mManifestMediaBrowserServices = new HashMap<>();
    private final Map<String, PsiClass> mServiceClasses = new HashMap<>();
    private final Map<String, JavaContext> mServiceClassContexts = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) { clear maps }

    @Override
    public void afterCheckEachProject(@NonNull Context context) { ... }

    // XmlScanner
    @Override
    public Collection<String> getApplicableElements() { return Collections.singletonList(TAG_SERVICE); }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) { ... }

    // JavaScanner
    @Override
    public List<Class<? extends PsiElement>> getApplicableNodeTypes() { return Collections.singletonList(PsiClass.class); }

    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new JavaElementVisitor() {
            @Override public void visitClass(PsiClass node) {
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) return;
                mServiceClasses.put(qualifiedName, node);
                mServiceClassContexts.put(qualifiedName, context);
                if (mManifestMediaBrowserServices.containsKey(qualifiedName) && !mReported.contains(qualifiedName) && !hasOnPlayFromSearch(node)) {
                    reportMissing(context, node);
                }
            }
        };
    }

    private boolean hasOnPlayFromSearch(PsiClass cls) { ... }
    private void reportMissing(JavaContext context, PsiClass cls) { ... }
    private static String resolveServiceName(XmlContext context, String name) { ... }
}