package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
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
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                            + "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckRootProject(Context context) {
        checkWebpFiles(context);
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public void filterIncident(Incident incident, Context context, LintMap map) {
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE 
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return file.getName().endsWith(".webp");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(context, node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    public void visitMethod(JavaContext context, UMethod node) {
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression node) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UMethod.class);
        types.add(UCallExpression.class);
        types.add(UClass.class);
        types.add(USimpleNameReferenceExpression.class);
        return types;
    }

    private void checkWebpFiles(Context context) {
        if (context.getProject().getMinSdkVersion().getFeatureLevel() >= 18) {
            return;
        }
        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        List<File> resourceFolders = context.getProject().getResourceFolders();
        for (File res : resourceFolders) {
            File[] drawables = res.listFiles();
            if (drawables != null) {
                for (File dir : drawables) {
                    if (dir.isDirectory() && (dir.getName().startsWith("drawable") || dir.getName().startsWith("mipmap"))) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.getName().endsWith(".webp")) {
                                    checkWebpFile(context, file, minSdk);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkWebpFile(Context context, File file, int minSdk) {
        if (minSdk < 15) {
            Incident incident = new Incident(ISSUE, "WebP requires API 15 (current min is " + minSdk + ")", context.getLocation(file));
            context.report(incident);
            return;
        }

        if (minSdk < 18) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] header = new byte[30];
                if (fis.read(header) >= 21) {
                    if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                        header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                        
                        String chunk = new String(header, 12, 4, StandardCharsets.US_ASCII);
                        if ("VP8L".equals(chunk)) {
                            Incident incident = new Incident(ISSUE, "Lossless WebP requires API 18 (current min is " + minSdk + ")", context.getLocation(file));
                            context.report(incident);
                        } else if ("VP8X".equals(chunk)) {
                            int flags = header[20] & 0xFF;
                            boolean hasAlpha = (flags & 0x10) != 0;
                            boolean hasAnimation = (flags & 0x02) != 0;
                            if (hasAlpha || hasAnimation) {
                                String feature = hasAlpha ? "Transparent" : "Animated";
                                Incident incident = new Incident(ISSUE, feature + " WebP requires API 18 (current min is " + minSdk + ")", context.getLocation(file));
                                context.report(incident);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore read errors
            }
        }
    }
}