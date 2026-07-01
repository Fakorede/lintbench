package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.IncidentFilter;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    Scope.combine(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                            + "really in the PNG format and not for example a GIF file named `.png`).",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public void filterIncident(
            @NonNull Incident incident,
            @NonNull Context context,
            @NonNull IncidentFilter filter) {
        super.filterIncident(incident, context, filter);
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        if (file.isFile()) {
            String name = file.getName();
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
                checkFolderAndExtension(context, file);
            }
        }
        return super.appliesTo(context, file);
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("image");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // XML-based checks can be performed here if necessary
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UMethod.class,
                UCallExpression.class,
                UClass.class,
                USimpleNameReferenceExpression.class);
    }

    @Nullable
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
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Source-based checks can be performed here if necessary
    }

    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        // Source-based checks can be performed here if necessary
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Source-based checks can be performed here if necessary
    }

    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        // Source-based checks can be performed here if necessary
    }

    private void checkFolderAndExtension(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        File parent = file.getParentFile();
        if (parent == null) return;
        String parentName = parent.getName();
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                return;
            }

            String ext = "";
            int index = name.lastIndexOf('.');
            if (index != -1) {
                ext = name.substring(index + 1).toLowerCase(Locale.US);
            }

            String actualFormat = null;
            if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                actualFormat = "png";
            } else if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46) {
                actualFormat = "gif";
            } else if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                actualFormat = "jpg";
            } else if (header[0] == (byte) 'R' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) 'F') {
                if (read >= 12 && header[8] == (byte) 'W' && header[9] == (byte) 'E' && header[10] == (byte) 'B' && header[11] == (byte) 'P') {
                    actualFormat = "webp";
                }
            }

            if (actualFormat != null) {
                boolean mismatch = false;
                if (actualFormat.equals("png") && !ext.equals("png")) {
                    mismatch = true;
                } else if (actualFormat.equals("gif") && !ext.equals("gif")) {
                    mismatch = true;
                } else if (actualFormat.equals("jpg") && !ext.equals("jpg") && !ext.equals("jpeg")) {
                    mismatch = true;
                } else if (actualFormat.equals("webp") && !ext.equals("webp")) {
                    mismatch = true;
                }

                if (mismatch) {
                    Location location = Location.create(file);
                    context.report(
                            ISSUE,
                            location,
                            String.format("Misleading file extension; named `.%s` but the file format is `%s`", ext, actualFormat)
                    );
                }
            }
        } catch (IOException e) {
            // Ignore reading errors on corrupted or locked files
        }
    }
}