public interface ResourceFolderScanner {
    /**
     * Called when a resource folder is encountered.
     */
    void checkResourceFolder(@NonNull ResourceContext context, @NonNull File folder);
}