package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_FILL_TYPE;
import static com.android.SdkConstants.ATTR_PATH_DATA;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_GRADIENT;
import static com.android.SdkConstants.TAG_PATH;
import static com.android.SdkConstants.TAG_VECTOR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class VectorDetector extends ResourceXmlDetector {

    private static final String EVEN_ODD = "evenOdd";

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or higher " +
            "is used, a vector drawable placed in the `drawable` folder is automatically " +
            "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n\n" +
            "However, there are some limitations to this raster image generation, and this " +
            "lint check flags elements and attributes that are not fully supported.  You " +
            "should manually check whether the generated output is acceptable for those " +
            "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private boolean mIsVector;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsVector = false;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (!mIsVector) {
            if (TAG_VECTOR.equals(tag)) {
                mIsVector = true;
            }
            return;
        }

        if (TAG_PATH.equals(tag)) {
            checkPath(context, element);
        } else if (TAG_CLIP_PATH.equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "`<clip-path>` is not supported when generating PNG images from vector "
                            + "drawables for older APIs");
        } else if (TAG_GRADIENT.equals(tag)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "`<gradient>` is not supported when generating PNG images from vector "
                            + "drawables for older APIs");
        }
    }

    private void checkPath(@NonNull XmlContext context, @NonNull Element element) {
        Attr fillTypeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FILL_TYPE);
        if (fillTypeAttr != null && EVEN_ODD.equals(fillTypeAttr.getValue())) {
            context.report(ISSUE, element,
                    context.getValueLocation(element, fillTypeAttr),
                    "fillType=\"evenOdd\" is not supported when generating PNG images from "
                            + "vector drawables for older APIs");
        }

        Attr pathDataAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_PATH_DATA);
        if (pathDataAttr != null && containsArcCommand(pathDataAttr.getValue())) {
            context.report(ISSUE, element,
                    context.getValueLocation(element, pathDataAttr),
                    "Arc commands (`A` or `a`) in pathData are not supported when generating "
                            + "PNG images from vector drawables for older APIs");
        }
    }

    private static boolean containsArcCommand(String pathData) {
        for (int i = 0, n = pathData.length(); i < n; i++) {
            char c = pathData.charAt(i);
            if (c == 'A' || c == 'a') {
                return true;
            }
        }
        return false;
    }
}