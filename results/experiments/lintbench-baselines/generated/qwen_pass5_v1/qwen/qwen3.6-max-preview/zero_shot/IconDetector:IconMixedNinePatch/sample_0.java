package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class IconDetector extends Detector {
    public static final Issue ISSUE = Issue.create(
        "IconMixedNinePatch",
        "Clashing PNG and 9-PNG files",
        "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
        "the image file and the nine patch file will both map to the same drawable " +
        "resource, `@drawable/file`, which is probably not what was intended.",
        Category.ICONS,
        6,
        Severity.ERROR,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableFolderNames() {
        return Arrays.asList("drawable", "mipmap");
    }

    @Override
    public void visitFolder(@NotNull ResourceFolderContext context) {
        File folder = context.getFolder();
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

        String resType = folder.getName();
        int dash = resType.indexOf('-');
        if (dash != -1) {
            resType = resType.substring(0, dash);
        }

        for (Map.Entry<String, File> entry : pngFiles.entrySet()) {
            String base = entry.getKey();
            File ninePatch = ninePatchFiles.get(base);
            if (ninePatch != null) {
                File png = entry.getValue();
                String message = String.format(
                    "Both `%s` and `%s` map to `@%s/%s`",
                    png.getName(), ninePatch.getName(), resType, base);
                context.report(ISSUE, Location.create(png), message);
            }
        }
    }
}