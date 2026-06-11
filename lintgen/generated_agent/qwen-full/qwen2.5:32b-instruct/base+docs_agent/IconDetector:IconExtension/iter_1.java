package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IconDetector extends Detector implements Detector.ResourceXmlScanner {

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
    public void afterCheckFile(@NonNull File file, @NonNull Project project) {
        if (ResourceFolderType.isResourceFolder(file.getParent())) {
            PsiDirectory dir = project.findDirectory(file.toPath());
            if (dir != null) {
                for (PsiElement child : dir.getChildren()) {
                    if (child instanceof PsiFile) {
                        checkIconFormat((PsiFile) child, project);
                    }
                }
            }
        }
    }

    private void checkIconFormat(@NonNull PsiFile psiFile, @NonNull Project project) {
        String fileName = psiFile.getName();
        File file = new File(psiFile.getVirtualFile().getPath());
        if (fileName.endsWith(SdkConstants.EXT_PNG)) {
            checkFileType(file, "PNG", project);
        } else if (fileName.endsWith(SdkConstants.EXT_JPG) || fileName.endsWith(SdkConstants.EXT_JPEG)) {
            checkFileType(file, "JFIF", project); // JPEG files often start with JFIF
        }
    }

    private void checkFileType(@NonNull File file, @NonNull String expectedHeader, @NonNull Project project) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length >= expectedHeader.length()) {
                String header = new String(bytes, 0, expectedHeader.length());
                if (!header.equals(expectedHeader)) {
                    report(project, file);
                }
            }
        } catch (IOException e) {
            // Ignore and continue
        }
    }

    private void report(@NonNull Project project, @NonNull File file) {
        PsiElement psiFile = project.findFileByPath(file.getPath());
        if (psiFile != null) {
            context().report(ISSUE,
                    psiFile,
                    context().getLocation(psiFile),
                    "Icon format does not match the file extension",
                    null);
        }
    }
}