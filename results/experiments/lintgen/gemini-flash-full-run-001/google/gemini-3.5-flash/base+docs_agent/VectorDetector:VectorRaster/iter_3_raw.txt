package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
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
        return Arrays.asList(
                "vector",
                "path",
                "clip-path",
                "gradient",
                "linearGradient",
                "radialGradient",
                "sweepGradient"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("vector")) {
            checkVector(context, element);
        } else if (tagName.equals("path")) {
            checkPath(context, element);
        } else if (tagName.equals("clip-path")) {
            checkClipPath(context, element);
        } else if (tagName.equals("gradient")
                || tagName.equals("linearGradient")
                || tagName.equals("radialGradient")
                || tagName.equals("sweepGradient")) {
            checkGradient(context, element);
        }
    }

    private void checkVector(XmlContext context, Element element) {
        Attr widthAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "width");
        Attr heightAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "height");
        if (widthAttr != null && heightAttr != null) {
            String widthStr = widthAttr.getValue();
            String heightStr = heightAttr.getValue();
            double width = getDpValue(widthStr);
            double height = getDpValue(heightStr);
            if (width > 200 || height > 200) {
                String message = String.format(
                        "Limit vector icons to 200x200 dp to avoid generating "
                                + "very large bitmap images; this icon is %s x %s",
                        widthStr, heightStr);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }

        if (isUseSupportLibrary(context)) {
            return;
        }

        int minSdk = 1;
        try {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        } catch (Throwable t) {
            // ignore
        }
        if (minSdk >= 21) {
            return;
        }
        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 21) {
            return;
        }

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
    }

    private void checkPath(XmlContext context, Element element) {
        if (isUseSupportLibrary(context)) {
            return;
        }

        int minSdk = 1;
        try {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        } catch (Throwable t) {
            // ignore
        }
        int folderVersion = context.getFolderVersion();

        Attr fillType = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "fillType");
        if (fillType != null) {
            if (minSdk < 24 && folderVersion < 24) {
                context.report(ISSUE, fillType, context.getLocation(fillType),
                        "The `fillType` attribute is not supported by the SVG-to-PNG generator");
            }
        }

        if (minSdk < 21 && folderVersion < 21) {
            checkThemeReference(context, element, "fillColor");
            checkThemeReference(context, element, "strokeColor");
        }
    }

    private void checkClipPath(XmlContext context, Element element) {
        if (isUseSupportLibrary(context)) {
            return;
        }

        int minSdk = 1;
        try {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        } catch (Throwable t) {
            // ignore
        }
        if (minSdk >= 21) {
            return;
        }
        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 21) {
            return;
        }

        context.report(ISSUE, element, context.getNameLocation(element),
                "The `<clip-path>` element is not supported by the SVG-to-PNG generator");
    }

    private void checkGradient(XmlContext context, Element element) {
        if (isUseSupportLibrary(context)) {
            return;
        }

        int minSdk = 1;
        try {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        } catch (Throwable t) {
            // ignore
        }
        if (minSdk >= 24) {
            return;
        }
        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 24) {
            return;
        }

        context.report(ISSUE, element, context.getNameLocation(element),
                "Gradients are not supported by the SVG-to-PNG generator");
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

    private static boolean isUseSupportLibrary(XmlContext context) {
        Project project = context.getProject();
        return project != null && Boolean.TRUE.equals(project.getSupportLibVectorDrawables());
    }

    private static double getDpValue(String value) {
        if (value == null) {
            return -1;
        }
        value = value.trim();
        if (value.endsWith("dp")) {
            try {
                return Double.parseDouble(value.substring(0, value.length() - 2).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        } else if (value.endsWith("dip")) {
            try {
                return Double.parseDouble(value.substring(0, value.length() - 3).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}