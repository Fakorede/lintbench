package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable resource, " +
            "`@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.RESOURCE_FOLDER)));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void checkFolder(ResourceContext context, String folderName) {
        File folder = context.file;
        if (folder == null) {
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

        for (Map.Entry<String, File> entry : pngFiles.entrySet()) {
            String base = entry.getKey();
            if (ninePatchFiles.containsKey(base)) {
                File pngFile = entry.getValue();
                String message = String.format("Both `%s.png` and `%s.9.png` exist in the same folder", base, base);
                context.report(ISSUE, Location.create(pngFile), message);
            }
        }
    }
}