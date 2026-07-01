package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_FILL_TYPE;
import static com.android.SdkConstants.TAG_ANIMATED_VECTOR;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_GRADIENT;
import static com.android.SdkConstants.TAG_PATH;
import static com.android.SdkConstants.TAG_VECTOR;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the `drawable` folder is "
                            + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and "
                            + "bitmap images are generated for different screen resolutions for backwards "
                            + "compatibility.\n"
                            + "\n"
                            + "However, there are some limitations to this raster image generation, and "
                            + "this lint check flags elements and attributes that are not fully supported. "
                            + "You should manually check whether the generated output is acceptable for "
                            + "those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, TAG_ANIMATED_VECTOR, TAG_PATH, TAG_GRADIENT, TAG_CLIP_PATH);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (isApiVersionedFolder(context)) {
            return;
        }

        String tag = element.getTagName();
        switch (tag) {
            case TAG_ANIMATED_VECTOR:
                report(
                        context,
                        element,
                        "Animated vector drawables are not supported for raster image generation");
                break;
            case TAG_GRADIENT:
                report(context, element, "Gradients are not supported for vector raster image generation");
                break;
            case TAG_CLIP_PATH:
                report(context, element, "Clip paths are not supported for vector raster image generation");
                break;
            case TAG_PATH:
                checkFillType(context, element);
                break;
            case TAG_VECTOR:
            default:
                break;
        }
    }

    private void checkFillType(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_FILL_TYPE)) {
            String fillType = element.getAttributeNS(ANDROID_URI, ATTR_FILL_TYPE);
            if ("evenOdd".equals(fillType)) {
                report(
                        context,
                        element,
                        "fillType=\"evenOdd\" is not supported for vector raster image generation");
            }
        }
    }

    private void report(@NonNull XmlContext context, @NonNull Element element, @NonNull String message) {
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private boolean isApiVersionedFolder(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile().getName();
        for (String segment : folderName.split("-")) {
            if (segment.matches("v\\d+")) {
                return true;
            }
        }
        return false;
    }
}