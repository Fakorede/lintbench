package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    @Override
    public void checkFolder(@NonNull ResourceFolderContext context, @NonNull File folder) {
        if (context.getFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - 6);
                ninePatchFiles.put(base, file);
            } else if (name.endsWith(".png")) {
                String base = name.substring(0, name.length() - 4);
                pngFiles.put(base, file);
            }
        }

        for (Map.Entry<String, File> entry : ninePatchFiles.entrySet()) {
            String base = entry.getKey();
            if (pngFiles.containsKey(base)) {
                File ninePatchFile = entry.getValue();
                File pngFile = pngFiles.get(base);

                Location location = context.getLocation(ninePatchFile);
                Location secondary = context.getLocation(pngFile);
                secondary.setMessage("Conflicting PNG file");
                location.setSecondary(secondary);

                context.report(
                        ISSUE,
                        location,
                        String.format("Clashing PNG and 9-PNG files: '%s.png' and '%s.9.png' both map to '@drawable/%s'", base, base, base)
                );
            }
        }
    }
}