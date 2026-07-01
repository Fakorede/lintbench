package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, ResourceFolderScanner {

    private static final String WEBP_EXTENSION = ".webp";
    private static final int MIN_API_WEBP = 15;
    private static final int MIN_API_WEBP_EXTENDED = 18;

    private static final byte[] RIFF_FOURCC = { 'R', 'I', 'F', 'F' };
    private static final byte[] WEBP_FOURCC = { 'W', 'E', 'B', 'P' };
    private static final byte[] VP8_FOURCC = { 'V', 'P', '8', ' ' };
    private static final byte[] VP8L_FOURCC = { 'V', 'P', '8', 'L' };
    private static final byte[] VP8X_FOURCC = { 'V', 'P', '8', 'X' };
    private static final byte[] ALPH_FOURCC = { 'A', 'L', 'P', 'H' };

    private static final String KEY_REQUIRED_API = "requiredApi";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format is only supported on Android 4.0 (API 15) and later. "
                            + "Lossless and transparent WebP images require Android 4.2.1 (API 18) and later.",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<WebpInfo> mPendingWebps = new ArrayList<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mPendingWebps.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        int minSdk = context.getMainProject().getMinSdk();
        for (WebpInfo info : mPendingWebps) {
            int requiredApi = info.requiresApi18() ? MIN_API_WEBP_EXTENDED : MIN_API_WEBP;
            String message = buildMessage(info, minSdk);
            LintMap map = new LintMap.Builder().put(KEY_REQUIRED_API, requiredApi).build();
            context.report(ISSUE, Location.create(info.file), message, map);
        }
        mPendingWebps.clear();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int requiredApi = map.getInt(KEY_REQUIRED_API, -1);
        if (requiredApi < 0) {
            return true;
        }
        return requiredApi > context.getMainProject().getMinSdk();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkResourceFolder(@NonNull ResourceContext context, @NonNull File folder) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE && folderType != ResourceFolderType.MIPMAP) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(WEBP_EXTENSION)) {
                mPendingWebps.add(createWebpInfo(file));
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // No XML element analysis is required for this check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for the WebP check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used for the WebP check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used for the WebP check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not used for the WebP check.
            }
        };
    }

    private static WebpInfo createWebpInfo(File file) {
        String name = file.getName();
        if (name.endsWith(WEBP_EXTENSION)) {
            name = name.substring(0, name.length() - WEBP_EXTENSION.length());
        }
        boolean[] features = parseWebpFile(file);
        return new WebpInfo(file, name, features[0], features[1], features[2]);
    }

    private static boolean[] parseWebpFile(File file) {
        boolean lossless = false;
        boolean alpha = false;
        boolean animation = false;
        boolean alphChunk = false;

        try (InputStream in = new FileInputStream(file)) {
            byte[] header = new byte[12];
            if (readFully(in, header) < 12) {
                return new boolean[3];
            }
            if (!matches(header, 0, RIFF_FOURCC) || !matches(header, 8, WEBP_FOURCC)) {
                return new boolean[3];
            }

            byte[] chunk = new byte[8];
            while (true) {
                int n = readFully(in, chunk);
                if (n < 8) {
                    break;
                }

                int chunkSize = (chunk[4] & 0xff)
                        | ((chunk[5] & 0xff) << 8)
                        | ((chunk[6] & 0xff) << 16)
                        | ((chunk[7] & 0xff) << 24);
                int paddedSize = chunkSize + (chunkSize & 1);

                if (matches(chunk, 0, VP8X_FOURCC)) {
                    byte[] data = new byte[paddedSize];
                    if (readFully(in, data) < paddedSize) {
                        break;
                    }
                    int flags = data[0] & 0xff;
                    animation = (flags & 0x01) != 0;
                    alpha = (flags & 0x08) != 0;
                } else if (matches(chunk, 0, VP8L_FOURCC)) {
                    lossless = true;
                    break;
                } else if (matches(chunk, 0, VP8_FOURCC)) {
                    break;
                } else if (matches(chunk, 0, ALPH_FOURCC)) {
                    alphChunk = true;
                    if (!skipFully(in, paddedSize)) {
                        break;
                    }
                } else {
                    if (!skipFully(in, paddedSize)) {
                        break;
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return new boolean[] { lossless, alpha || alphChunk, animation };
    }

    private static int readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        int remaining = buffer.length;
        while (remaining > 0) {
            int read = in.read(buffer, offset, remaining);
            if (read < 0) {
                break;
            }
            offset += read;
            remaining -= read;
        }
        return offset;
    }

    private static boolean skipFully(InputStream in, long amount) throws IOException {
        long skipped = 0;
        while (skipped < amount) {
            long n = in.skip(amount - skipped);
            if (n <= 0) {
                int b = in.read();
                if (b < 0) {
                    return false;
                }
                skipped++;
            } else {
                skipped += n;
            }
        }
        return true;
    }

    private static boolean matches(byte[] buffer, int offset, byte[] fourcc) {
        if (buffer.length < offset + fourcc.length) {
            return false;
        }
        for (int i = 0; i < fourcc.length; i++) {
            if (buffer[offset + i] != fourcc[i]) {
                return false;
            }
        }
        return true;
    }

    private static String buildMessage(WebpInfo info, int minSdk) {
        if (info.lossless) {
            return String.format(Locale.US,
                    "WebP lossless images are not supported on this platform (requires API %d, current min is %d)",
                    MIN_API_WEBP_EXTENDED, minSdk);
        }
        if (info.alpha) {
            return String.format(Locale.US,
                    "WebP images with transparency are not supported on this platform (requires API %d, current min is %d)",
                    MIN_API_WEBP_EXTENDED, minSdk);
        }
        if (info.animation) {
            return String.format(Locale.US,
                    "Animated WebP images are not supported on this platform (requires API %d, current min is %d)",
                    MIN_API_WEBP_EXTENDED, minSdk);
        }
        return String.format(Locale.US,
                "WebP images are not supported on this platform (requires API %d, current min is %d)",
                MIN_API_WEBP, minSdk);
    }

    private static class WebpInfo {
        final File file;
        final String name;
        final boolean lossless;
        final boolean alpha;
        final boolean animation;

        WebpInfo(File file, String name, boolean lossless, boolean alpha, boolean animation) {
            this.file = file;
            this.name = name;
            this.lossless = lossless;
            this.alpha = alpha;
            this.animation = animation;
        }

        boolean requiresApi18() {
            return lossless || alpha || animation;
        }
    }
}