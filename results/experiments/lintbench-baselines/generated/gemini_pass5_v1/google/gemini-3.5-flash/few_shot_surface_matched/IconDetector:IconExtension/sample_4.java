package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

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
                            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)));

    @Override
    public void beforeCheckRootProject(Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        super.afterCheckEachProject(context);
        for (java.io.File folder : context.getProject().getResourceFolders()) {
            java.io.File[] drawables = folder.listFiles();
            if (drawables != null) {
                for (java.io.File file : drawables) {
                    if (file.isDirectory()) {
                        java.io.File[] files = file.listFiles();
                        if (files != null) {
                            for (java.io.File f : files) {
                                checkIcon(context, f);
                            }
                        }
                    } else {
                        checkIcon(context, file);
                    }
                }
            }
        }
    }

    private void checkIcon(Context context, java.io.File file) {
        String name = file.getName().toLowerCase(java.util.Locale.US);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
            try (java.io.InputStream is = new java.io.FileInputStream(file)) {
                byte[] header = new byte[8];
                int read = is.read(header);
                if (read >= 4) {
                    String ext = "";
                    if (header[0] == (byte) 137 && header[1] == (byte) 80 && header[2] == (byte) 78 && header[3] == (byte) 79) {
                        ext = "png";
                    } else if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                        ext = "jpg";
                    } else if (header[0] == (byte) 'G' && header[1] == (byte) 'I' && header[2] == (byte) 'F') {
                        ext = "gif";
                    } else if (header[0] == (byte) 'R' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) 'F') {
                        ext = "webp";
                    }

                    if (!ext.isEmpty()) {
                        if (name.endsWith(".png") && !ext.equals("png")) {
                            report(context, file, "png", ext);
                        } else if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && !ext.equals("jpg")) {
                            report(context, file, "jpg", ext);
                        } else if (name.endsWith(".gif") && !ext.equals("gif")) {
                            report(context, file, "gif", ext);
                        } else if (name.endsWith(".webp") && !ext.equals("webp")) {
                            report(context, file, "webp", ext);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                // ignore
            }
        }
    }

    private void report(Context context, java.io.File file, String expected, String actual) {
        context.report(
                ISSUE,
                Location.create(file),
                "Icon format does not match the file extension: expected " + expected + " but was " + actual);
    }

    @Override
    public void filterIncident(Incident incident) {
        super.filterIncident(incident);
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return super.appliesTo(context, file);
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("image");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                // Dummy implementation to satisfy the overridden requirement
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                // Dummy implementation to satisfy the overridden requirement
            }

            @Override
            public void visitClass(UClass node) {
                // Dummy implementation to satisfy the overridden requirement
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                // Dummy implementation to satisfy the overridden requirement
            }
        };
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(
                UMethod.class,
                UCallExpression.class,
                UClass.class,
                USimpleNameReferenceExpression.class
        );
    }
}