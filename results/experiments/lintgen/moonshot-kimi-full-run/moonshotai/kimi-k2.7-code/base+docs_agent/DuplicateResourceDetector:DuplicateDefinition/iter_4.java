package com.android.tools.lint.detector.api;

public interface ResourceFolderScanner {
    /**
     * Called for each resource file in a resource folder.
     */
    void checkResourceFile(@NonNull ResourceContext context, @NonNull File file);
}