public interface UastScanner {
    @Nullable
    default List<Class<? extends UElement>> getApplicableUastNodeTypes() { return null; }
    @Nullable
    default UElementHandler createUastHandler(@NotNull JavaContext context) { return null; }
    ...
}