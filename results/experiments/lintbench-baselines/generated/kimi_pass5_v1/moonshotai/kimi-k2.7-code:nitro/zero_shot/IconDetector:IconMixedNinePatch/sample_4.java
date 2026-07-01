package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends ResourceFolderDetector {

    private static final String PNG_EXTENSION = ".png";
    private static final String NINE_PATCH_EXTENSION = ".9.png";

    public static final Issue ICON_MIXED_NINE_PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same drawable "
                    + "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<File, Map<String, File>> mBaseNamesByFolder;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mBaseNamesByFolder = new HashMap<>();
    }

    @Override
    public void checkFile(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();

        boolean isNinePatch = name.endsWith(NINE_PATCH_EXTENSION);
        if (!isNinePatch && !name.endsWith(PNG_EXTENSION)) {
            return;
        }

        String baseName = isNinePatch
                ? name.substring(0, name.length() - NINE_PATCH_EXTENSION.length())
                : name.substring(0, name.length() - PNG_EXTENSION.length());

        File folder = file.getParentFile();

        Map<String, File> baseNames = mBaseNamesByFolder.get(folder);
        if (baseNames == null) {
            baseNames = new HashMap<>();
            mBaseNamesByFolder.put(folder, baseNames);
        }

        File existing = baseNames.get(baseName);
        if (existing != null) {
            boolean existingIsNinePatch = existing.getName().endsWith(NINE_PATCH_EXTENSION);
            if (existingIsNinePatch != isNinePatch) {
                ResourceFolderType folderType = context.getResourceFolderType();
                String type = folderType == ResourceFolderType.MIPMAP ? "mipmap" : "drawable";

                Location location = Location.create(file);
                Location secondary = Location.create(existing);
                location.setSecondary(secondary);

                context.report(ICON_MIXED_NINE_PATCH, location,
                        "Found both `" + existing.getName() + "` and `" + name + "` in `"
                                + folder.getName() + "`; they both resolve to @"
                                + type + "/" + baseName);
            }
        } else {
            baseNames.put(baseName, file);
        }
    }
}