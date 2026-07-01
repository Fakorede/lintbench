package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                    + "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        File file = context.getFile();
        if (file != null && isIconFile(file)) {
            checkIconFormat(file, context, element);
        }
    }

    private boolean isIconFile(@NonNull File file) {
        String name = file.getName();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
               name.endsWith(".gif") || name.endsWith(".webp");
    }

    private void checkIconFormat(@NonNull File file, @NonNull XmlContext context, @NonNull Element element) {
        String ext = getExtension(file.getName());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 4) return;

            boolean mismatch = false;
            if ("png".equals(ext)) {
                mismatch = !(header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                             header[2] == (byte) 0x4E && header[3] == (byte) 0x47);
            } else if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                mismatch = !(header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);
            } else if ("gif".equals(ext)) {
                mismatch = !(header[0] == (byte) 0x47 && header[1] == (byte) 0x49 &&
                             header[2] == (byte) 0x46 && header[3] == (byte) 0x38);
            } else if ("webp".equals(ext)) {
                mismatch = !(header[0] == (byte) 0x52 && header[1] == (byte) 0x49 &&
                             header[2] == (byte) 0x46 && header[3] == (byte) 0x46 &&
                             header[8] == (byte) 0x57 && header[9] == (byte) 0x45 &&
                             header[10] == (byte) 0x42 && header[11] == (byte) 0x50);
            }

            if (mismatch) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Icon format does not match the file extension");
            }
        } catch (IOException ignored) {
        }
    }

    private String getExtension(@NonNull String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            }

            @Override
            public List<Class<? extends UElement>> getApplicableUastTypes() {
                return Arrays.asList(
                        UClass.class,
                        UMethod.class,
                        UCallExpression.class,
                        USimpleNameReferenceExpression.class
                );
            }
        };
    }
}