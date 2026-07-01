package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class VectorDetector extends Detector implements XmlScanner {

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";

    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_HEIGHT = "height";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";
    private static final String ATTR_STROKE_LINE_CAP = "strokeLineCap";
    private static final String ATTR_STROKE_LINE_JOIN = "strokeLineJoin";
    private static final String ATTR_STROKE_MITER_LIMIT = "strokeMiterLimit";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_TINT = "tint";
    private static final String ATTR_TINT_MODE = "tintMode";

    private static final Set<String> UNSUPPORTED_ATTRIBUTES = ImmutableSet.of(
            ATTR_TRIM_PATH_START,
            ATTR_TRIM_PATH_END,
            ATTR_TRIM_PATH_OFFSET,
            ATTR_STROKE_LINE_CAP,
            ATTR_STROKE_LINE_JOIN,
            ATTR_STROKE_MITER_LIMIT,
            ATTR_AUTO_MIRRORED,
            ATTR_TINT,
            ATTR_TINT_MODE
    );

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector image generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is "
                    + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` "
                    + "and bitmap images are generated for different screen resolutions for "
                    + "backwards compatibility.\n\n"
                    + "However, there are some limitations to this raster image generation, "
                    + "and this lint check flags elements and attributes that are not fully "
                    + "supported. You should manually check whether the generated output is "
                    + "acceptable for those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return ImmutableSet.of(TAG_VECTOR, TAG_CLIP_PATH, TAG_GRADIENT);
    }

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return ImmutableSet.<String>builder()
                .add(ATTR_FILL_TYPE)
                .addAll(UNSUPPORTED_ATTRIBUTES)
                .build();
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!isVectorDrawable(element.getOwnerDocument())) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        String tag = element.getTagName();

        if (TAG_VECTOR.equals(tag)) {
            checkSize(context, element, minSdk);
        } else if (TAG_CLIP_PATH.equals(tag) && minSdk < 21) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<clip-path>` elements are not supported when vector drawables are "
                            + "rasterized for older devices.");
        } else if (TAG_GRADIENT.equals(tag) && minSdk < 24) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<gradient>` elements are not supported when vector drawables are "
                            + "rasterized for older devices.");
        }
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!isVectorDrawable(attribute.getOwnerDocument())) {
            return;
        }

        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdk();
        String name = attribute.getLocalName();

        if (ATTR_FILL_TYPE.equals(name)) {
            if (minSdk < 24) {
                reportAttribute(context, attribute);
            }
        } else if (UNSUPPORTED_ATTRIBUTES.contains(name) && minSdk < 21) {
            reportAttribute(context, attribute);
        }
    }

    private static void checkSize(
            @NotNull XmlContext context, @NotNull Element element, int minSdk) {
        if (minSdk >= 21) {
            return;
        }

        String width = element.getAttributeNS(ANDROID_URI, ATTR_WIDTH);
        String height = element.getAttributeNS(ANDROID_URI, ATTR_HEIGHT);

        if (isLargeDimension(width) || isLargeDimension(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "This vector icon is very large (width=%1$s, height=%2$s) and "
                                    + "may produce large bitmaps when rasterized for older "
                                    + "devices. Consider using a smaller vector or a bitmap.",
                            width,
                            height));
        }
    }

    private static boolean isLargeDimension(String value) {
        if (value == null || !value.endsWith("dp")) {
            return false;
        }
        try {
            float v = Float.parseFloat(value.substring(0, value.length() - 2));
            return v > 200;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void reportAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String name = attribute.getLocalName();
        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Attribute `android:" + name + "` is not supported when vector drawables are "
                        + "rasterized for older devices.");
    }

    private static boolean isVectorDrawable(Document document) {
        if (document == null) {
            return false;
        }
        Element root = document.getDocumentElement();
        return root != null && TAG_VECTOR.equals(root.getTagName());
    }
}