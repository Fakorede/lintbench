package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class VectorDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "VectorRaster",
        "Vector Image Generation",
        "Vector icons require API 21 or API 24 depending on used features, " +
        "but when minSdkVersion is less than 21 or 24 and Android Gradle plugin 1.4 or " +
        "higher is used, a vector drawable placed in the drawable folder is automatically " +
        "moved to drawable-anydpi-v21 or drawable-anydpi-v24 and bitmap images are " +
        "generated for different screen resolutions for backwards compatibility.\n\n" +
        "However, there are some limitations to this raster image generation, and this " +
        "lint check flags elements and attributes that are not fully supported. " +
        "You should manually check whether the generated output is acceptable for those " +
        "older devices.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_PATH_DATA = "pathData";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, TAG_PATH, TAG_CLIP_PATH, TAG_GRADIENT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }
        if (context.getFolderVersion() >= 21) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdkVersion().getApiLevel();
        if (minSdk >= 21) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_CLIP_PATH.equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                "<clip-path> is not fully supported for vector raster generation on older devices");
        } else if (TAG_GRADIENT.equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                "<gradient> is not fully supported for vector raster generation on older devices");
        } else if (TAG_PATH.equals(tag)) {
            String fillType = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_FILL_TYPE);
            if (fillType != null && !fillType.isEmpty()) {
                context.report(ISSUE, element, context.getLocation(element),
                    "android:fillType is not fully supported for vector raster generation on older devices");
            }
            String pathData = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_PATH_DATA);
            if (pathData != null && (pathData.indexOf('A') != -1 || pathData.indexOf('a') != -1)) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Arc commands (A/a) in android:pathData are not fully supported for vector raster generation on older devices");
            }
        }
    }
}