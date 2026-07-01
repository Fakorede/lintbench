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
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ICON_MIXED_NINE_PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                    + "the image file and the nine patch file will both map to the same "
                    + "drawable resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            3,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    private static final String DOT_PNG = ".png";
    private static final String DOT_9PNG = ".9.png";

    private Map<String, File> mRegularPngs;
    private Map<String, File> mNinePatchPngs;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mRegularPngs = new HashMap<>();
        mNinePatchPngs = new HashMap<>();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            if (name.endsWith(DOT_9PNG)) {
                String base = name.substring(0, name.length() - DOT_9PNG.length());
                mNinePatchPngs.put(base, file);
            } else if (name.endsWith(DOT_PNG)) {
                String base = name.substring(0, name.length() - DOT_PNG.length());
                mRegularPngs.put(base, file);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, File> entry : mNinePatchPngs.entrySet()) {
            String base = entry.getKey();
            File ninePatch = entry.getValue();
            File regular = mRegularPngs.get(base);
            if (regular == null) {
                continue;
            }

            String message = String.format(Locale.US,
                    "The `%1$s` and `%2$s` files both map to the same drawable resource, `@drawable/%3$s`",
                    ninePatch.getName(),
                    regular.getName(),
                    base);

            context.report(ICON_MIXED_NINE_PATCH, Location.create(regular), message);
            context.report(ICON_MIXED_NINE_PATCH, Location.create(ninePatch), message);
        }
    }
}