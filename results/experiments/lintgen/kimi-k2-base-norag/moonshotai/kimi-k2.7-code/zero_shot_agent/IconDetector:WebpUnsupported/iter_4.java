public abstract class Detector {
    ...
    public interface XmlScanner { ... }
    public interface SourceScanner { ... }
    public interface ClassScanner { ... }
    public interface BinaryResourceScanner {
        @NonNull
        Collection<ResourceFolderType> getApplicableResourceFolders();
        void checkBinaryResource(@NonNull ResourceContext context);
    }
    public interface ResourceFolderScanner {
        @NonNull
        Collection<ResourceFolderType> getApplicableFolders();
        void checkFolder(@NonNull ResourceContext context, @NonNull File folder);
    }
    ...
}