package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.FileType;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, ResourceFolderScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). "
                            + "Lossless encoding and transparency require Android 4.2.1 (API 18).",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final int API_WEBP_BASIC = 15;
    private static final int API_WEBP_LOSSLESS_ALPHA = 18;

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final Pattern API_PATTERN = Pattern.compile("API (\\d+)");
    private static final Pattern VERSION_PATTERN = Pattern.compile("-v(\\d+)$");

    private final Map<String, WebpInfo> mWebps = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mWebps.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mWebps.clear();
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        Matcher matcher = API_PATTERN.matcher(incident.getMessage());
        int required = API_WEBP_BASIC;
        if (matcher.find()) {
            required = Integer.parseInt(matcher.group(1));
        }
        int minSdk = context.getMainProject().getMinSdkVersion();
        return minSdk < required;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public FileType getApplicableFileType() {
        return FileType.BINARY_FILE;
    }

    @Override
    public void visit(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        if (!name.endsWith(".webp") && !name.endsWith(".WEBP")) {
            return;
        }
        WebpInfo info = parseWebp(file);
        if (info == null) {
            return;
        }
        String folder = file.getParentFile().getName();
        int folderVersion = getFolderVersion(folder);
        if (folderVersion >= info.required) {
            return;
        }
        mWebps.merge(info.name, info, (a, b) -> a.required <= b.required ? a : b);
        reportIncident(context, Location.create(file), info.required, info.name);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"bitmap".equals(element.getTagName())) {
            return;
        }
        String src = element.getAttributeNS(ANDROID_URI, "src");
        if (src == null || src.isEmpty()) {
            return;
        }
        if (!src.startsWith("@drawable/") && !src.startsWith("@mipmap/")) {
            return;
        }
        String name = src.substring(src.lastIndexOf('/') + 1);
        WebpInfo info = mWebps.get(name);
        if (info == null) {
            return;
        }
        Location location = context.getValueLocation(element, "src");
        reportIncident(context, location, info.required, info.name);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used; references are handled by the UAST handler.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not used.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not used.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                String name = node.getIdentifier();
                WebpInfo info = mWebps.get(name);
                if (info == null) {
                    return;
                }
                PsiElement resolved = node.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiField field = (PsiField) resolved;
                PsiClass cls = field.getContainingClass();
                if (cls == null) {
                    return;
                }
                String innerName = cls.getName();
                if (!"drawable".equals(innerName) && !"mipmap".equals(innerName)) {
                    return;
                }
                PsiClass outer = cls.getContainingClass();
                if (outer == null || !"R".equals(outer.getName())) {
                    return;
                }
                reportIncident(context, context.getLocation(node), info.required, info.name);
            }
        };
    }

    private void reportIncident(
            @NonNull Context context,
            @NonNull Location location,
            int required,
            @NonNull String name) {
        String message;
        if (required == API_WEBP_LOSSLESS_ALPHA) {
            message =
                    "WebP \""
                            + name
                            + "\" uses lossless encoding or transparency and requires API 18";
        } else {
            message = "WebP \"" + name + "\" requires API 15";
        }
        context.report(ISSUE, location, message);
    }

    private WebpInfo parseWebp(@NonNull File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[12];
            if (in.read(header) != 12) {
                return null;
            }
            if (!"RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                    || !"WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
                return null;
            }
            byte[] chunk = new byte[8];
            while (in.read(chunk) == 8) {
                String type = new String(chunk, 0, 4, StandardCharsets.US_ASCII);
                int size =
                        (chunk[4] & 0xff)
                                | ((chunk[5] & 0xff) << 8)
                                | ((chunk[6] & 0xff) << 16)
                                | ((chunk[7] & 0xff) << 24);
                if ("VP8 ".equals(type)) {
                    return new WebpInfo(getBaseName(file), API_WEBP_BASIC);
                }
                if ("VP8L".equals(type)) {
                    return new WebpInfo(getBaseName(file), API_WEBP_LOSSLESS_ALPHA);
                }
                if ("VP8X".equals(type)) {
                    byte[] flags = new byte[10];
                    if (in.read(flags) != 10) {
                        return new WebpInfo(getBaseName(file), API_WEBP_LOSSLESS_ALPHA);
                    }
                    if ((flags[0] & 0x10) != 0) {
                        return new WebpInfo(getBaseName(file), API_WEBP_LOSSLESS_ALPHA);
                    }
                    size = Math.max(0, size - 10);
                }
                int skip = size + (size & 1);
                while (skip > 0) {
                    long skipped = in.skip(skip);
                    if (skipped <= 0) {
                        break;
                    }
                    skip -= skipped;
                }
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }
        return null;
    }

    private int getFolderVersion(@NonNull String folderName) {
        Matcher matcher = VERSION_PATTERN.matcher(folderName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    @NonNull
    private String getBaseName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static class WebpInfo {
        final String name;
        final int required;

        WebpInfo(String name, int required) {
            this.name = name;
            this.required = required;
        }
    }
}