package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFile;
import com.android.tools.lint.detector.api.ResourceFolder;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

    @Override
    public void checkFolder(@NotNull Context context, @NotNull ResourceFolder folder) {
        String folderName = folder.getName();
        if (!folderName.startsWith("drawable")) {
            return;
        }

        Collection<ResourceFile> files = folder.getFiles();
        Set<String> pngBaseNames = new HashSet<>();
        Set<String> ninePatchBaseNames = new HashSet<>();

        for (ResourceFile file : files) {
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                ninePatchBaseNames.add(name.substring(0, name.length() - 6));
            } else if (name.endsWith(".png")) {
                pngBaseNames.add(name.substring(0, name.length() - 4));
            }
        }

        for (String baseName : ninePatchBaseNames) {
            if (pngBaseNames.contains(baseName)) {
                ResourceFile ninePatchFile = folder.getFile(baseName + ".9.png");
                ResourceFile pngFile = folder.getFile(baseName + ".png");
                if (ninePatchFile != null && pngFile != null) {
                    Location location = Location.create(ninePatchFile.getFile());
                    Location secondary = Location.create(pngFile.getFile());
                    location.setSecondary(secondary);
                    context.report(ISSUE, location, String.format(
                            "The files `%1$s` and `%2$s` both map to the same drawable resource name `%3$s`",
                            ninePatchFile.getName(), pngFile.getName(), baseName));
                }
            }
        }
    }
}