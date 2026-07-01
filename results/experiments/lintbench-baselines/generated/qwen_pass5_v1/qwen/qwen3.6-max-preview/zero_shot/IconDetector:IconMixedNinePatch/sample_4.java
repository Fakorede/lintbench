package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderInfo;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IconDetector extends Detector implements ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FOLDER_SCOPE)
    );

    @Override
    public List<String> getApplicableFolderNames() {
        return Arrays.asList("drawable", "mipmap");
    }

    @Override
    public void checkFolder(ResourceFolderInfo folder) {
        Collection<String> fileNames = folder.getFileNames();
        if (fileNames == null || fileNames.isEmpty()) {
            return;
        }

        Map<String, String> pngFiles = new HashMap<>();
        Map<String, String> ninePatchFiles = new HashMap<>();

        for (String name : fileNames) {
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - 6);
                ninePatchFiles.put(base, name);
            } else if (name.endsWith(".png")) {
                String base = name.substring(0, name.length() - 4);
                pngFiles.put(base, name);
            }
        }

        Set<String> clashes = new HashSet<>(ninePatchFiles.keySet());
        clashes.retainAll(pngFiles.keySet());

        if (clashes.isEmpty()) {
            return;
        }

        Context context = folder.getContext();
        File dir = folder.getFolder();

        for (String base : clashes) {
            String pngName = pngFiles.get(base);
            String ninePatchName = ninePatchFiles.get(base);

            File pngFile = new File(dir, pngName);
            File ninePatchFile = new File(dir, ninePatchName);

            Location location = Location.create(ninePatchFile)
                    .withSecondary(Location.create(pngFile), "Conflicting PNG file");

            context.report(ISSUE, location,
                    String.format("Both `%s` and `%s` exist and will map to the same drawable resource `@drawable/%s`",
                            ninePatchName, pngName, base));
        }
    }
}