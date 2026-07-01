package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                            + "really in the PNG format and not for example a GIF file named `.png`).",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (File resFolder : context.getProject().getResourceFolders()) {
            checkResources(context, resFolder);
        }
    }

    private void checkResources(@NonNull Context context, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                    checkResources(context, file);
                }
            } else if (file.isFile()) {
                if (!checkFormatMatchesExtension(file)) {
                    context.report(
                            ISSUE,
                            Location.create(file),
                            "Icon format does not match the file extension");
                }
            }
        }
    }

    private static boolean checkFormatMatchesExtension(File file) {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".gif") && !name.endsWith(".webp")) {
            return true;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 4) {
                return true;
            }

            String actualFormat = null;
            if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                actualFormat = "png";
            } else if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                actualFormat = "jpg";
            } else if (header[0] == (byte) 'G' && header[1] == (byte) 'I' && header[2] == (byte) 'F') {
                actualFormat = "gif";
            } else if (read >= 12 &&
                       header[0] == (byte) 'R' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) 'F' &&
                       header[8] == (byte) 'W' && header[9] == (byte) 'E' && header[10] == (byte) 'B' && header[11] == (byte) 'P') {
                actualFormat = "webp";
            }

            if (actualFormat == null) {
                return true;
            }

            if (name.endsWith(".png") && !actualFormat.equals("png")) {
                return false;
            }
            if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && !actualFormat.equals("jpg")) {
                return false;
            }
            if (name.endsWith(".gif") && !actualFormat.equals("gif")) {
                return false;
            }
            if (name.endsWith(".webp") && !actualFormat.equals("webp")) {
                return false;
            }
        } catch (IOException e) {
            // Ignore descriptor read exceptions
        }
        return true;
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident, @NonNull Context context) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // XML checks handled via resource scanning
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Stub to satisfy required method overrides
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Stub to satisfy required method overrides
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Stub to satisfy required method overrides
    }

    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Stub to satisfy required method overrides
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }
}