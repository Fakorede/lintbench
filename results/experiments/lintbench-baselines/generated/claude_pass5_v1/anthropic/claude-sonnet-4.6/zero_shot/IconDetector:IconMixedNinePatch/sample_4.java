package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import com.android.resources.ResourceFolderType;

import java.io.File;
import java.util.*;

public class IconDetector extends ResourceFolderScanner {

    public static final Issue ICON_MIXED_NINE_PATCH = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.ALL_RESOURCES_SCOPE
            )
    );

    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE ||
               folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<Issue> getIssues() {
        return Collections.singletonList(ICON_MIXED_NINE_PATCH);
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        // Collect all .png and .9.png basenames
        Set<String> pngNames = new HashSet<>();
        Set<String> ninePatchNames = new HashSet<>();

        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".9.png")) {
                String base = name.substring(0, name.length() - ".9.png".length());
                ninePatchNames.add(base);
            } else if (name.endsWith(".png")) {
                String base = name.substring(0, name.length() - ".png".length());
                pngNames.add(base);
            }
        }

        // Find clashes
        for (String base : ninePatchNames) {
            if (pngNames.contains(base)) {
                // Find the actual file to report on
                File ninePatchFile = new File(folder, base + ".9.png");
                File pngFile = new File(folder, base + ".png");

                // Report on the nine-patch file, mentioning the clash
                Location location = Location.create(ninePatchFile);
                Location secondary = Location.create(pngFile);
                secondary.setMessage("This file clashes with the nine-patch file");
                location.setSecondary(secondary);

                context.report(
                        ICON_MIXED_NINE_PATCH,
                        location,
                        String.format(
                                "The files `%1$s.png` and `%1$s.9.png` both map to " +
                                "the same drawable resource `@drawable/%1$s`",
                                base
                        )
                );
            }
        }
    }
}