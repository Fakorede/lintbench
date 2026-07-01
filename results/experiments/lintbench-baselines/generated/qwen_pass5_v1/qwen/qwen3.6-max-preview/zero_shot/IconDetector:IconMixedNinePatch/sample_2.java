package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends Detector {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    @Override
    public Collection<String> getApplicableFolderNames() {
        return Arrays.asList(SdkConstants.FD_RES_DRAWABLE, SdkConstants.FD_RES_MIPMAP);
    }

    @Override
    public void visitFolder(Context context) {
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(SdkConstants.DOT_9PNG)) {
                String baseName = name.substring(0, name.length() - SdkConstants.DOT_9PNG.length());
                ninePatchFiles.put(baseName, file);
            } else if (name.endsWith(SdkConstants.DOT_PNG)) {
                String baseName = name.substring(0, name.length() - SdkConstants.DOT_PNG.length());
                pngFiles.put(baseName, file);
            }
        }

        for (Map.Entry<String, File> entry : ninePatchFiles.entrySet()) {
            String baseName = entry.getKey();
            File ninePatchFile = entry.getValue();
            File pngFile = pngFiles.get(baseName);

            if (pngFile != null) {
                Location location = Location.create(ninePatchFile);
                location.setSecondary(Location.create(pngFile));
                String message = String.format(
                        "The 9-patch file `%1$s` and the PNG file `%2$s` will both map to the same drawable resource name (`@drawable/%3$s`)",
                        ninePatchFile.getName(), pngFile.getName(), baseName);
                context.report(ISSUE, location, message);
            }
        }
    }
}