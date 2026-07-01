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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class VectorDetector extends Detector implements XmlScanner {

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";

    private static final String ATTR_TRIM_PATH_START = "trimPathStart";
    private static final String ATTR_TRIM_PATH_END = "trimPathEnd";
    private static final String ATTR_TRIM_PATH_OFFSET = "trimPathOffset";
    private static final String ATTR_STROKE_LINE_CAP = "strokeLineCap";
    private static final String ATTR_STROKE_LINE_JOIN = "strokeLineJoin";
    private static final String ATTR_STROKE_MITER_LIMIT = "strokeMiterLimit";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_TINT = "tint";
    private static final String ATTR_TINT_MODE = "tintMode";
    private static final String ATTR_FILL_TYPE = "fillType";

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

    private boolean mIsVectorDrawable;

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        Element root = document.getDocumentElement();
        mIsVectorDrawable = root != null && TAG_VECTOR.equals(root.getTagName());
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!mIsVectorDrawable) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        String tag = element.getTagName();

        if (TAG_CLIP_PATH.equals(tag) && minSdk < 21) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The `<clip-path>` element is not fully supported when vector drawables "
                            + "are rasterized for older devices; verify the generated output.");
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node node = attributes.item(i);
            if (!(node instanceof Attr)) {
                continue;
            }
            Attr attr = (Attr) node;
            if (!ANDROID_URI.equals(attr.getNamespaceURI())) {
                continue;
            }
            String name = attr.getLocalName();
            if (ATTR_FILL_TYPE.equals(name)) {
                if (minSdk < 24) {
                    reportAttribute(context, attr);
                }
            } else if (UNSUPPORTED_ATTRIBUTES.contains(name) && minSdk < 21) {
                reportAttribute(context, attr);
            }
        }
    }

    private static void reportAttribute(@NotNull XmlContext context, @NotNull Attr attr) {
        String name = attr.getLocalName();
        context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "Attribute `android:" + name + "` is not fully supported when vector drawables "
                        + "are rasterized for older devices; verify the generated output.");
    }
}