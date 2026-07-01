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
                            java.util.EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
                    )
            );

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
        // No-op setup
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
        java.util.List<java.io.File> resourceFolders = context.getProject().getResourceFolders();
        for (java.io.File res : resourceFolders) {
            java.io.File[] folders = res.listFiles();
            if (folders != null) {
                for (java.io.File folder : folders) {
                    String folderName = folder.getName();
                    if (folderName.startsWith("drawable") || folderName.startsWith("mipmap")) {
                        java.io.File[] files = folder.listFiles();
                        if (files != null) {
                            for (java.io.File file : files) {
                                checkFile(context, file);
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkFile(Context context, java.io.File file) {
        String name = file.getName().toLowerCase(java.util.Locale.US);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp")) {
            if (file.isFile() && file.length() > 12) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    byte[] header = new byte[12];
                    int read = fis.read(header);
                    if (read >= 12) {
                        String expectedFormat = null;
                        String actualFormat = null;

                        if (name.endsWith(".png")) {
                            expectedFormat = "PNG";
                        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                            expectedFormat = "JPEG";
                        } else if (name.endsWith(".gif")) {
                            expectedFormat = "GIF";
                        } else if (name.endsWith(".webp")) {
                            expectedFormat = "WEBP";
                        }

                        if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                            actualFormat = "PNG";
                        } else if (header[0] == (byte) 0x47 && header[1] == (byte) 0x49 && header[2] == (byte) 0x46 && header[3] == (byte) 0x38) {
                            actualFormat = "GIF";
                        } else if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                            actualFormat = "JPEG";
                        } else if (header[0] == (byte) 'R' && header[1] == (byte) 'I' && header[2] == (byte) 'F' && header[3] == (byte) 'F'
                                && header[8] == (byte) 'W' && header[9] == (byte) 'E' && header[10] == (byte) 'B' && header[11] == (byte) 'P') {
                            actualFormat = "WEBP";
                        }

                        if (expectedFormat != null && actualFormat != null && !expectedFormat.equals(actualFormat)) {
                            Location location = Location.create(file);
                            context.report(
                                    ISSUE,
                                    location,
                                    "Icon format does not match the file extension (expected " + expectedFormat + ", but was " + actualFormat + ")"
                            );
                        }
                    }
                } catch (java.io.IOException e) {
                    // Ignore read errors
                }
            }
        }
    }

    @Override
    public void filterIncident(@com.android.annotations.NonNull Incident incident) {
        // No-op filtering
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("image");
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        // XML-based checks can be added here if needed
    }

    @com.android.annotations.Nullable
    @Override
    public UElementHandler createUastHandler(@com.android.annotations.NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@com.android.annotations.NonNull UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitMethod(@com.android.annotations.NonNull UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(@com.android.annotations.NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UMethod method) {
        // Source-based method checking
    }

    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression call) {
        // Source-based call checking
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
        // Source-based class checking
    }

    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression reference) {
        // Source-based reference checking
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<java.lang.Class<? extends UElement>> getApplicableUastTypes() {
        java.util.List<java.lang.Class<? extends UElement>> types = new java.util.ArrayList<>();
        types.add(UClass.class);
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }
}