public class RegistrationDetector extends Detector
        implements Detector.ClassScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            RegistrationDetector.class,
            Scope.JAVA_FILE_SCOPE,
            Scope.MANIFEST_SCOPE);

    /** Activity, service and provider classes not registered in the manifest */
    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            ...
            IMPLEMENTATION);

    private final Map<Project, Set<String>> mRegistered = new HashMap<>();
    private final Map<Project, List<Pair<String, Location>>> mLocations = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        ...
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        ...
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CLASS_ACTIVITY, CLASS_SERVICE, CLASS_CONTENT_PROVIDER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        ...
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE, TAG_PROVIDER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        ...
    }

    private static String resolveManifestName(...) { ... }
}