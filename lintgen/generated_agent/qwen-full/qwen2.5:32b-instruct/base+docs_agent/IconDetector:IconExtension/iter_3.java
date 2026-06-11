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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IconDetector extends Detector implements ResourceXmlScanner {

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
    public void afterCheckFile(@NonNull File file, @NonNull Context context) {
        if (ResourceFolderType.isResourceFolder(file.getParent())) {
            Project project = context.getProject();
            PsiDirectory dir = project.getBaseDir().getParent();
            if (dir != null) {
                for (PsiElement child : dir.getChildren()) {
                    if (child instanceof PsiFile) {
                        checkIconFormat((PsiFile) child, file, context);
                    }
                }
            }
        }
    }

    private void checkIconFormat(@NonNull PsiFile psiFile, @NonNull File file, @NonNull Context context) {
        String fileName = psiFile.getName();
        if (fileName.endsWith(SdkConstants.EXT_PNG)) {
            checkFileType(file, "PNG", context);
        } else if (fileName.endsWith("jpg") || fileName.endsWith("jpeg")) {
            checkFileType(file, "JFIF", context); // JPEG files often start with JFIF
        }
    }

    private void checkFileType(@NonNull File file, @NonNull String expectedHeader, @NonNull Context context) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length >= expectedHeader.length()) {
                String header = new String(bytes, 0, expectedHeader.length());
                if (!header.equals(expectedHeader)) {
                    report(context, file);
                }
            }
        } catch (IOException e) {
            // Ignore and continue
        }
    }

    private void report(@NonNull Context context, @NonNull File file) {
        PsiElement psiFile = context.getProject().findFile(file);
        if (psiFile != null) {
            Location location = Location.create(psiFile.getContainingFile());
            context.report(ISSUE,
                    psiFile,
                    location,
                    "Icon format does not match the file extension",
                    null);
        }
    }

    @Override
    public void visitResource(@NonNull File file, @NonNull Context context) {
        // No-op for now.
    }
}