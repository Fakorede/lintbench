public interface ResourceFolderScanner {
        void checkFolder(@NonNull ResourceContext context);
        boolean appliesTo(@NonNull ResourceFolderType folderType);
    }