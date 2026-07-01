package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    private static final String PNG = ".png";
    private static final String NINE_PATCH = ".9.png";

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
    public Collection<String> getApplicableDirs() {
        return Arrays.asList("drawable", "drawable-");
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceContext context, @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> ninePatchFiles = new HashMap<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(NINE_PATCH)) {
                String base = name.substring(0, name.length() - NINE_PATCH.length());
                ninePatchFiles.put(base, file);
            }
        }

        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(PNG) && !name.endsWith(NINE_PATCH)) {
                String base = name.substring(0, name.length() - PNG.length());
                File ninePatchFile = ninePatchFiles.get(base);
                if (ninePatchFile != null) {
                    String message = String.format(
                            "The resource `@drawable/%1$s` has both a PNG file (`%2$s`) and a 9-patch PNG file (`%3$s`), which both map to `@drawable/%1$s`. Rename one of them.",
                            base, name, ninePatchFile.getName());
                    context.report(ISSUE, Location.create(file), message);
                }
            }
        }
    }
}