package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NonNull;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements Detector.ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE));

    @NonNull
    @Override
    public List<ResourceFolderType> getApplicableFolderTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void visitFolder(@NonNull Context context, @NonNull ResourceFolderType folderType, @NonNull List<ResourceFile> files) {
        Map<String, File> pngFiles = new HashMap<>();
        Map<String, File> ninePatchFiles = new HashMap<>();

        for (ResourceFile resourceFile : files) {
            File file = resourceFile.getFile();
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
            File pngFile = entry.getValue();
            File ninePatchFile = ninePatchFiles.get(base);
            if (ninePatchFile != null) {
                String message = String.format(
                        "Both `%s.png` and `%s.9.png` exist; both will map to `@drawable/%s`",
                        base, base, base);
                Location location = Location.create(pngFile, Location.create(ninePatchFile));
                context.report(ISSUE, location, message);
            }
        }
    }
}