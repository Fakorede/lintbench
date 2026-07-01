package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.BinaryResourceContext;
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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class IconDetector extends Detector
        implements SourceCodeScanner, XmlScanner, BinaryResourceScanner {

    private static final String WEBP = "webp";
    private static final int API_WEBP = 15;
    private static final int API_WEBP_LOSSLESS_TRANSPARENT = 18;

    private static final List<String> APPLICABLE_ELEMENTS =
            Arrays.asList(
                    "bitmap",
                    "item",
                    "clip",
                    "scale",
                    "rotate",
                    "inset",
                    "transition",
                    "animated-rotate",
                    "layer-list",
                    "level-list",
                    "selector",
                    "shape",
                    "animation-list");

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP image format is not supported on Android versions prior to 4.0 "
                            + "(API 15). In addition, lossless and transparent WebP images are "
                            + "not supported prior to Android 4.2.1 (API 18); API 17 is 4.2.0.",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private int mMinSdk = -1;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMinSdk = context.getMainProject().getMinSdk();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No per-project cleanup required; filtering is done in filterIncident.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (mMinSdk < 0) {
            return true;
        }
        int requiredApi = map.get("requiresApi", API_WEBP);
        return mMinSdk < requiredApi;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return APPLICABLE_ELEMENTS;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attribute = attributes.item(i);
            String value = attribute.getNodeValue();
            if (value != null && isWebPReference(value)) {
                reportWebp(
                        context,
                        context.getLocation(attribute),
                        API_WEBP,
                        "WebP image references require Android 4.0 (API 15) or later");
            }
        }
    }

    @Override
    public Collection<String> getApplicableExtensions() {
        return Collections.singletonList(WEBP);
    }

    @Override
    public void checkBinaryResource(@NonNull BinaryResourceContext context) {
        WebPFeatures features = parseWebPFeatures(context.file);
        int requiredApi =
                features.isLossless || features.hasAlpha
                        ? API_WEBP_LOSSLESS_TRANSPARENT
                        : API_WEBP;
        String message;
        if (requiredApi == API_WEBP_LOSSLESS_TRANSPARENT) {
            message =
                    "Lossless or transparent WebP images are not supported on Android versions "
                            + "prior to 4.2.1 (API 18)";
        } else {
            message = "WebP images require Android 4.0 (API 15) or later";
        }
        reportWebp(context, Location.create(context.file), requiredApi, message);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used directly; createUastHandler performs the source-level checks.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No method-level check required.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                for (UExpression argument : node.getValueArguments()) {
                    if (argument instanceof ULiteralExpression) {
                        Object value = ((ULiteralExpression) argument).getValue();
                        if (value instanceof String && isWebPReference((String) value)) {
                            reportWebp(
                                    context,
                                    context.getLocation(argument),
                                    API_WEBP,
                                    "WebP image references require Android 4.0 (API 15) or later");
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                if (!"WEBP".equals(node.getIdentifier())) {
                    return;
                }
                PsiElement resolved = node.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                if (containingClass == null
                        || !"CompressFormat".equals(containingClass.getName())) {
                    return;
                }
                PsiClass outerClass = containingClass.getContainingClass();
                if (outerClass != null && "Bitmap".equals(outerClass.getName())) {
                    reportWebp(
                            context,
                            context.getLocation(node),
                            API_WEBP,
                            "Bitmap.CompressFormat.WEBP requires Android 4.0 (API 15) or later");
                }
            }
        };
    }

    private void reportWebp(
            @NonNull Context context,
            @NonNull Location location,
            int requiredApi,
            @NonNull String message) {
        LintMap.Builder builder = new LintMap.Builder();
        builder.put("requiresApi", requiredApi);
        context.report(new Incident(ISSUE, location, message, builder.build()));
    }

    private static boolean isWebPReference(@NonNull String value) {
        return value.toLowerCase().contains("." + WEBP);
    }

    private static WebPFeatures parseWebPFeatures(@NonNull File file) {
        WebPFeatures features = new WebPFeatures();
        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return features;
        }
        if (data.length < 12
                || !equalsAscii(data, 0, "RIFF")
                || !equalsAscii(data, 8, "WEBP")) {
            return features;
        }
        int offset = 12;
        while (offset + 8 <= data.length) {
            String tag = new String(data, offset, 4, StandardCharsets.US_ASCII);
            int size =
                    (data[offset + 4] & 0xFF)
                            | ((data[offset + 5] & 0xFF) << 8)
                            | ((data[offset + 6] & 0xFF) << 16)
                            | ((data[offset + 7] & 0xFF) << 24);
            offset += 8;

            if ("VP8L".equals(tag)) {
                features.isLossless = true;
            } else if ("VP8X".equals(tag) && size >= 10) {
                int flags = data[offset] & 0xFF;
                if ((flags & 0x08) != 0) {
                    features.hasAlpha = true;
                }
            }

            if (features.isLossless && features.hasAlpha) {
                break;
            }

            if ((size & 1) != 0) {
                size++;
            }
            offset += size;
            if (offset < 0 || offset > data.length) {
                break;
            }
        }
        return features;
    }

    private static boolean equalsAscii(
            @NonNull byte[] data, int offset, @NonNull String ascii) {
        byte[] bytes = ascii.getBytes(StandardCharsets.US_ASCII);
        if (offset + bytes.length > data.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (data[offset + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static final class WebPFeatures {
        boolean isLossless;
        boolean hasAlpha;
    }
}