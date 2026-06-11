package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

public class IconDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "IncorrectIconFormat",
            "The icon format does not match the file extension.",
            "Ensure that icons have the correct file extension (e.g., a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            6, // Priority
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCES_WITHOUT_LAYOUTS_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UFile.class);
    }

    @NotNull
    @Override
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitUFile(UFile node) {
                checkIconFormat(context, node.getJavaPsi().getContainingFile().getVirtualFile());
            }
        };
    }

    private void checkIconFormat(JavaContext context, com.intellij.openapi.vfs.VirtualFile virtualFile) {
        if (virtualFile == null || !virtualFile.exists()) {
            return;
        }

        File file = new File(virtualFile.getPath());

        try {
            String extension = Files.getFileExtension(file.getName());
            boolean isValidFormat = isCorrectIconFormat(file, extension);

            if (!isValidFormat) {
                Location location = context.getLocation(file);
                context.report(ISSUE, null, location, "The icon format does not match the file extension.");
            }
        } catch (IOException e) {
            // Handle exception
        }
    }

    private boolean isCorrectIconFormat(File file, String extension) throws IOException {
        byte[] signature = new byte[4];
        try (java.io.InputStream inputStream = Files.newInputStream(file.toPath())) {
            if (inputStream.read(signature) != 4) {
                return false;
            }
        }

        switch (extension.toLowerCase()) {
            case "png":
                return signature[0] == (byte) 0x89 && signature[1] == 'P' && signature[2] == 'N' && signature[3] == 'G';
            case "jpg":
            case "jpeg":
                return signature[0] == (byte) 0xFF && signature[1] == (byte) 0xD8;
            case "gif":
                return signature[0] == 'G' && signature[1] == 'I' && signature[2] == 'F';
            default:
                return true; // Assume correct if unknown extension
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceType.DRAWABLE.isOfType(folderType);
    }
}