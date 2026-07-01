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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class VectorDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "minSdkVersion is less than 21 or 24 and Android Gradle plugin 1.4 or higher " +
            "is used, a vector drawable placed in the drawable folder is automatically " +
            "moved to drawable-anydpi-v21 or drawable-anydpi-v24 and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n\n" +
            "However, there are some limitations to this raster image generation, and this " +
            "lint check flags elements and attributes that are not fully supported. You " +
            "should manually check whether the generated output is acceptable for those " +
            "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "path", "clip-path", "gradient", "linearGradient", "radialGradient", "sweepGradient");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (isUseSupportLibrary(context)) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        int folderVersion = context.getFolderVersion();

        String tagName = element.getTagName();

        if (tagName.equals("gradient") || tagName.equals("linearGradient") || tagName.equals("radialGradient") || tagName.equals("sweepGradient")) {
            if (minSdk < 24 && folderVersion < 24) {
                context.report(ISSUE, element, context.getNameLocation(element),
                        "Gradients are not supported by the SVG-to-PNG generator");
            }
        } else {
            if (minSdk < 21 && folderVersion < 21) {
                if (tagName.equals("vector")) {
                    Attr alpha = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "alpha");
                    if (alpha != null) {
                        context.report(ISSUE, alpha, context.getLocation(alpha),
                                "The `alpha` attribute on `<vector>` is not supported by the SVG-to-PNG generator");
                    }
                    Attr autoMirrored = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "autoMirrored");
                    if (autoMirrored != null) {
                        context.report(ISSUE, autoMirrored, context.getLocation(autoMirrored),
                                "The `autoMirrored` attribute is not supported by the SVG-to-PNG generator");
                    }
                } else if (tagName.equals("clip-path")) {
                    context.report(ISSUE, element, context.getNameLocation(element),
                            "The `<clip-path>` element is not supported by the SVG-to-PNG generator");
                } else if (tagName.equals("path")) {
                    Attr fillType = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "fillType");
                    if (fillType != null) {
                        context.report(ISSUE, fillType, context.getLocation(fillType),
                                "The `fillType` attribute is not supported by the SVG-to-PNG generator");
                    }
                    checkThemeReference(context, element, "fillColor");
                    checkThemeReference(context, element, "strokeColor");
                }
            }
        }
    }

    private void checkThemeReference(XmlContext context, Element element, String attributeName) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attributeName);
        if (attr != null) {
            String value = attr.getValue();
            if (value.startsWith("?") || value.startsWith("@android:attr/") || value.startsWith("@attr/")) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Theme references in colors are not supported by the SVG-to-PNG generator");
            }
        }
    }

    private boolean isUseSupportLibrary(XmlContext context) {
        Boolean supportLib = context.getProject().getSupportLibVectorDrawables();
        return supportLib != null && supportLib;
    }
}