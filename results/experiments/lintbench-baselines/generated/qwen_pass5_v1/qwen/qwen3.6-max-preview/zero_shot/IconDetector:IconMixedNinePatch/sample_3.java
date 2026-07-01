package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements OtherFileScanner {
    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<File, Map<String, List<File>>> folderFiles = new HashMap<>();

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.RESOURCE_FILE_SCOPE;
    }

    @Override
    public void visitFile(Context context, File file) {
        String name = file.getName();
        String resName = null;
        if (name.endsWith(".9.png")) {
            resName = name.substring(0, name.length() - 6);
        } else if (name.endsWith(".png")) {
            resName = name.substring(0, name.length() - 4);
        }

        if (resName != null) {
            folderFiles
                    .computeIfAbsent(file.getParentFile(), k -> new HashMap<>())
                    .computeIfAbsent(resName, k -> new ArrayList<>())
                    .add(file);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<File, Map<String, List<File>>> folderEntry : folderFiles.entrySet()) {
            for (Map.Entry<String, List<File>> nameEntry : folderEntry.getValue().entrySet()) {
                List<File> files = nameEntry.getValue();
                if (files.size() > 1) {
                    boolean hasPng = false;
                    boolean has9Png = false;
                    File pngFile = null;
                    File ninePatchFile = null;

                    for (File f : files) {
                        if (f.getName().endsWith(".9.png")) {
                            has9Png = true;
                            ninePatchFile = f;
                        } else if (f.getName().endsWith(".png")) {
                            hasPng = true;
                            pngFile = f;
                        }
                    }

                    if (hasPng && has9Png) {
                        String message = String.format(
                                "Both `%s` and `%s` map to the same drawable resource `@drawable/%s`",
                                pngFile.getName(), ninePatchFile.getName(), nameEntry.getKey());
                        Location location = Location.create(pngFile);
                        location.setSecondary(Location.create(ninePatchFile));
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
        folderFiles.clear();
    }
}