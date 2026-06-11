package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.uast.UCallExpression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class IconDetector extends Detector implements Detector.UastScanner {
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
    public List<String> getApplicableMethodNames() {
        return ImmutableList.of("getResources");
    }

    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return ImmutableList.of(UCallExpression.class);
    }

    @Override
    public void visitCallExpression(@NonNull UCallExpression node, @NonNull UClassContext context) {
        // No need to check method calls for this issue.
    }

    @Nullable
    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public void afterCheckFile(@NonNull File file, @NonNull Scope scope, @NonNull Context context) {
        if (scope == Scope.RESOURCE_FILE_SCOPE && ResourceFolderType.isResourceFolder(file.getParent())) {
            PsiDirectory dir = context.getProject().findDirectory(file.toPath());
            if (dir != null) {
                for (PsiElement child : dir.getChildren()) {
                    if (child instanceof PsiFile) {
                        checkIconFormat((PsiFile) child, context);
                    }
                }
            }
        }
    }

    private void checkIconFormat(@NonNull PsiFile psiFile, @NonNull Context context) {
        String fileName = psiFile.getName();
        File file = new File(psiFile.getVirtualFile().getPath());
        if (fileName.endsWith(SdkConstants.EXT_PNG)) {
            checkFileType(file, "PNG", context);
        } else if (fileName.endsWith(SdkConstants.EXT_JPG) || fileName.endsWith(SdkConstants.EXT_JPEG)) {
            checkFileType(file, "JFIF", context); // JPEG files often start with JFIF
        }
    }

    private void checkFileType(@NonNull File file, @NonNull String expectedHeader, @NonNull Context context) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length >= expectedHeader.length()) {
                String header = new String(bytes, 0, expectedHeader.length());
                if (!header.equals(expectedHeader)) {
                    context.report(ISSUE,
                            psiFile(file),
                            context.getLocation(psiFile(file)),
                            "Icon format does not match the file extension",
                            null);
                }
            }
        } catch (IOException e) {
            // Ignore and continue
        }
    }

    @NonNull
    private PsiElement psiFile(@NonNull File file) {
        return context.getProject().findFileByPath(file.getPath());
    }
}