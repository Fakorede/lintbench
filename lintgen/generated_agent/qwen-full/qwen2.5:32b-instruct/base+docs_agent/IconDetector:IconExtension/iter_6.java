package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IconDetector extends Detector implements FileContent {
    private static final String ISSUE_ID = "IncorrectIconFormat";
    private static final String ISSUE_NAME = "Icon format does not match the file extension";
    private static final String ISSUE_EXPLANATION =
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            ISSUE_NAME,
            ISSUE_EXPLANATION,
            Category.CORRECTNESS,
            5, // Priority
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitFile(@NonNull Context context, @NonNull File file) {
        if (ResourceFolderType.isResourceFolder(file.getParentFile())) {
            checkIconFormat(context, file);
        }
    }

    private void checkIconFormat(@NonNull Context context, @NonNull File file) {
        String fileName = file.getName();
        if (fileName.endsWith(SdkConstants.EXT_PNG)) {
            checkFileType(context, file, "\u0089\u0050\u004E\u0047"); // PNG files often start with this header
        } else if (fileName.endsWith("jpg") || fileName.endsWith("jpeg")) {
            checkFileType(context, file, "\u00FF\u00D8\u00FF"); // JPEG files often start with SOI marker
        }
    }

    private void checkFileType(@NonNull Context context, @NonNull File file, @NonNull String expectedHeader) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length >= expectedHeader.getBytes().length) {
                String header = new String(bytes, 0, expectedHeader.getBytes().length);
                if (!header.equals(expectedHeader)) {
                    report(context, file);
                }
            }
        } catch (IOException e) {
            // Ignore and continue
        }
    }

    private void report(@NonNull Context context, @NonNull File file) {
        Location location = Location.create(file);
        context.report(ISSUE,
                location,
                "Icon format does not match the file extension");
    }
}