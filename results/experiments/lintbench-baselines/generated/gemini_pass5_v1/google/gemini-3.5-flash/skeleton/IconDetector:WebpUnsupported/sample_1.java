package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, "
                            + "such as lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No-op
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No-op
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Arrays.asList(USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No-op
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // No-op
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                if ("WEBP".equals(name) || "WEBP_LOSSLESS".equals(name) || "WEBP_LOSSY".equals(name)) {
                    PsiElement resolved = node.resolve();
                    if (resolved instanceof PsiField) {
                        PsiField field = (PsiField) resolved;
                        PsiClass containingClass = field.getContainingClass();
                        if (containingClass != null && "android.graphics.Bitmap.CompressFormat".equals(containingClass.getQualifiedName())) {
                            int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
                            if ("WEBP".equals(name)) {
                                if (minSdk < 15) {
                                    Incident incident = new Incident(
                                            ISSUE,
                                            "WebP requires API 15 (current min is " + minSdk + ")",
                                            context.getLocation(node));
                                    context.report(incident);
                                }
                            } else {
                                if (minSdk < 30) {
                                    Incident incident = new Incident(
                                            ISSUE,
                                            "WebP lossless/lossy encoding requires API 30 (current min is " + minSdk + ")",
                                            context.getLocation(node));
                                    context.report(incident);
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(".webp")) {
            int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
            if (minSdk < 15) {
                Incident incident = new Incident(
                        ISSUE,
                        "WebP decodes are not supported on Android versions < 4.0 (API 15); current min SDK is " + minSdk,
                        Location.create(file));
                context.report(incident);
            } else if (minSdk < 18) {
                WebpHeader header = WebpHeader.get(file);
                if (header != null && (header.lossless || header.alpha)) {
                    String message = "WebP lossless and transparency decodes are not supported on Android versions < 4.3 (API 18); current min SDK is " + minSdk;
                    Incident incident = new Incident(ISSUE, message, Location.create(file));
                    context.report(incident);
                }
            }
        }
    }

    private static class WebpHeader {
        public final boolean lossless;
        public final boolean alpha;

        public WebpHeader(boolean lossless, boolean alpha) {
            this.lossless = lossless;
            this.alpha = alpha;
        }

        @Nullable
        public static WebpHeader get(@NonNull File file) {
            try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
                byte[] header = new byte[12];
                if (stream.read(header) != 12) {
                    return null;
                }
                if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                        || header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') {
                    return null;
                }
                byte[] chunkHeader = new byte[8];
                if (stream.read(chunkHeader) != 8) {
                    return null;
                }
                String chunkType = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = ((chunkHeader[4] & 0xFF))
                        | ((chunkHeader[5] & 0xFF) << 8)
                        | ((chunkHeader[6] & 0xFF) << 16)
                        | ((chunkHeader[7] & 0xFF) << 24);

                if ("VP8X".equals(chunkType)) {
                    int flags = stream.read();
                    if (flags == -1) return null;
                    boolean alpha = (flags & 0x10) != 0;
                    boolean lossless = false;
                    long bytesToSkip = chunkSize - 1;
                    if (bytesToSkip > 0) {
                        long skipped = stream.skip(bytesToSkip);
                        while (skipped < bytesToSkip) {
                            long r = stream.skip(bytesToSkip - skipped);
                            if (r <= 0) break;
                            skipped += r;
                        }
                    }
                    while (true) {
                        if (stream.read(chunkHeader) != 8) {
                            break;
                        }
                        chunkType = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                        chunkSize = ((chunkHeader[4] & 0xFF))
                                | ((chunkHeader[5] & 0xFF) << 8)
                                | ((chunkHeader[6] & 0xFF) << 16)
                                | ((chunkHeader[7] & 0xFF) << 24);
                        if ("VP8L".equals(chunkType)) {
                            lossless = true;
                        } else if ("ALPH".equals(chunkType)) {
                            alpha = true;
                        }
                        if (chunkSize > 0) {
                            long actualSize = (chunkSize + 1) & ~1;
                            long skipped = stream.skip(actualSize);
                            while (skipped < actualSize) {
                                long r = stream.skip(actualSize - skipped);
                                if (r <= 0) break;
                                skipped += r;
                            }
                        }
                    }
                    return new WebpHeader(lossless, alpha);
                } else if ("VP8L".equals(chunkType)) {
                    return new WebpHeader(true, false);
                } else if ("VP8 ".equals(chunkType)) {
                    return new WebpHeader(false, false);
                }
            } catch (IOException e) {
                // Ignore
            }
            return null;
        }
    }
}