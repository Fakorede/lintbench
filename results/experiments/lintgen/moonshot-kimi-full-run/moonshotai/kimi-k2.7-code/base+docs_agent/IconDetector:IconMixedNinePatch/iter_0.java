package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same "
                    + "drawable resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    @Override
    @NonNull
    public Collection<ResourceFolderType> getApplicableFolders() {
        return EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context) {
        File folder = context.getResourceFolder();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder);
        String prefix = folderType == ResourceFolderType.MIPMAP ? "mipmap" : "drawable";

        Map<String, File> pngFiles = new HashMap<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(".png") && !name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - ".png".length());
                pngFiles.put(base, file);
            }
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - ".9.png".length());
                File pngFile = pngFiles.get(base);
                if (pngFile != null) {
                    String message = String.format(
                            "The resource `@%s/%s` has both a PNG file (`%s`) and a 9-patch PNG file (`%s`), "
                                    + "which both map to `@%s/%s`. Rename one of them.",
                            prefix, base, pngFile.getName(), name, prefix, base);
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        }
    }
}