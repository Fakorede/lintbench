public enum Scope {
    ALL,
    ALL_RESOURCE_FILES,
    JAVA_FILE,
    RESOURCE_FILE,
    BINARY_RESOURCE_FILE,
    MANIFEST,
    GRADLE_FILE,
    PROTOTYPE,
    OTHER;

    public static final EnumSet<Scope> ALL_SCOPE = EnumSet.allOf(Scope.class);
    public static final EnumSet<Scope> RESOURCE_FILE_SCOPE = EnumSet.of(RESOURCE_FILE);
    public static final EnumSet<Scope> ALL_RESOURCES_SCOPE = EnumSet.of(RESOURCE_FILE, BINARY_RESOURCE_FILE);
    ...
}