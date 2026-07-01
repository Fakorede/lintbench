public class ResourceContext extends Context {
    @NonNull private final ResourceFolderType mFolderType;
    private final int mFolderVersion;
    @Nullable private final File mResourceFile;

    public ResourceContext(@NonNull LintDriver driver, @NonNull Project project, @NonNull File file, @NonNull ResourceFolderType folderType, int folderVersion) {
        super(driver, project, file);
        mFolderType = folderType;
        mFolderVersion = folderVersion;
    }

    @Override
    @NonNull
    public File getFile() { return super.getFile(); }

    @NonNull
    public ResourceFolderType getResourceFolderType() { return mFolderType; }

    public int getFolderVersion() { return mFolderVersion; }

    @Nullable
    public File getResourceFile() { return mResourceFile; }
}