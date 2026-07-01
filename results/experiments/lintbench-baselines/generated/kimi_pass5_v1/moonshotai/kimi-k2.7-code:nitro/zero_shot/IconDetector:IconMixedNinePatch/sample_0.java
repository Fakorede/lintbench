package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector {

    private static final String PNG_EXTENSION = ".png";
    private static final String NINE_PATCH_EXTENSION = ".9.png";

    private final Map<String, File> mPngs = new HashMap<String, File>();
    private final Map<String, File> mNinePatchPngs = new HashMap<String, File>();
    private final Set<String> mReported = new HashSet<String>();

    public static final Issue ICON_MIXED_NINE_PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            7,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mPngs.clear();
        mNinePatchPngs.clear();
        mReported.clear();
    }

    @Override
    public void visitBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        boolean isNinePatch;
        String baseName;
        if (name.endsWith(NINE_PATCH_EXTENSION)) {
            isNinePatch = true;
            baseName = name.substring(0, name.length() - NINE_PATCH_EXTENSION.length());
        } else if (name.endsWith(PNG_EXTENSION)) {
            isNinePatch = false;
            baseName = name.substring(0, name.length() - PNG_EXTENSION.length());
        } else {
            return;
        }

        if (isNinePatch) {
            mNinePatchPngs.put(baseName, file);
            File other = mPngs.get(baseName);
            if (other != null && mReported.add(baseName)) {
                Location location = Location.create(file);
                location.setSecondary(Location.create(other));
                context.report(ICON_MIXED_NINE_PATCH, location,
                        "Found both `%1$s.png` and `%1$s.9.png` resources for the same drawable resource `@drawable/%1$s`",
                        baseName);
            }
        } else {
            mPngs.put(baseName, file);
            File other = mNinePatchPngs.get(baseName);
            if (other != null && mReported.add(baseName)) {
                Location location = Location.create(file);
                location.setSecondary(Location.create(other));
                context.report(ICON_MIXED_NINE_PATCH, location,
                        "Found both `%1$s.png` and `%1$s.9.png` resources for the same drawable resource `@drawable/%1$s`",
                        baseName);
            }
        }
    }
}