package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class VectorDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "VectorRaster",
        "Vector Image Generation",
        "Vector icons require API 21 or API 24 depending on used features, " +
        "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
        "higher is used, a vector drawable placed in the `drawable` folder is automatically " +
        "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
        "generated for different screen resolutions for backwards compatibility.\n\n" +
        "However, there are some limitations to this raster image generation, and this " +
        "lint check flags elements and attributes that are not fully supported. " +
        "You should manually check whether the generated output is acceptable for those " +
        "older devices.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final List<String> API_24_ATTRIBUTES = Arrays.asList(
        "fillType",
        "trimPathStart",
        "trimPathEnd",
        "trimPathOffset",
        "strokeLineCap",
        "strokeLineJoin",
        "strokeMiterLimit"
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_PATH);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        if (context.getFolderVersion() >= 24) {
            return;
        }

        com.android.sdklib.AndroidVersion minSdkVersion = context.getMainProject().getMinSdkVersion();
        if (minSdkVersion != null && minSdkVersion.getApiLevel() >= 24) {
            return;
        }

        for (String attrName : API_24_ATTRIBUTES) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
            if (attr != null) {
                context.report(ISSUE, attr, context.getLocation(attr),
                    String.format("`%s` is not fully supported when rasterizing vector drawables for API < 24", attrName));
            }
        }
    }
}