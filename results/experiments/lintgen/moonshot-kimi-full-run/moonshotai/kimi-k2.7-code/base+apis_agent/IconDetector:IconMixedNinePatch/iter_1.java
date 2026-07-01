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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private final Map<String, List<File>> mRegularPngs = new HashMap<>();
    private final Map<String, List<File>> mNinePatchPngs = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mRegularPngs.clear();
        mNinePatchPngs.clear();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.getFile();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - ".9.png".length());
                mNinePatchPngs.computeIfAbsent(base, k -> new ArrayList<>()).add(file);
            } else if (name.endsWith(".png")) {
                String base = name.substring(0, name.length() - ".png".length());
                mRegularPngs.computeIfAbsent(base, k -> new ArrayList<>()).add(file);
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, List<File>> entry : mNinePatchPngs.entrySet()) {
            String base = entry.getKey();
            List<File> ninePatchFiles = entry.getValue();
            List<File> regularFiles = mRegularPngs.get(base);
            if (regularFiles == null || regularFiles.isEmpty()) {
                continue;
            }

            File firstRegular = regularFiles.get(0);
            for (File ninePatch : ninePatchFiles) {
                String message = String.format(Locale.US,
                        "`%1$s` clashes with `%2$s`; both map to `@drawable/%3$s`",
                        ninePatch.getName(),
                        firstRegular.getName(),
                        base);
                context.report(ICON_MIXED_NINE_PATCH, Location.create(ninePatch), message);
            }

            File firstNinePatch = ninePatchFiles.get(0);
            for (File regular : regularFiles) {
                String message = String.format(Locale.US,
                        "`%1$s` clashes with `%2$s`; both map to `@drawable/%3$s`",
                        regular.getName(),
                        firstNinePatch.getName(),
                        base);
                context.report(ICON_MIXED_NINE_PATCH, Location.create(regular), message);
            }
        }
    }
}