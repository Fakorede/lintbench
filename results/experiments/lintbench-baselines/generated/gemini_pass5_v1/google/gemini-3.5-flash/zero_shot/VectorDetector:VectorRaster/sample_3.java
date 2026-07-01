package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTO_MIRRORED;
import static com.android.SdkConstants.ATTR_FILL_TYPE;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_VECTOR;

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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, "path", TAG_CLIP_PATH, "gradient");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        Boolean useSupportLibrary = context.getProject().getVectorDrawablesUseSupportLibrary();
        if (Boolean.TRUE.equals(useSupportLibrary)) {
            return;
        }

        int minSdk = context.getProject().getMinSdk();
        if (minSdk >= 21) {
            if (minSdk >= 24) {
                return;
            }
            if (!"gradient".equals(element.getTagName())) {
                return;
            }
        }

        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 21) {
            if (folderVersion >= 24 || !"gradient".equals(element.getTagName())) {
                return;
            }
        }

        String tagName = element.getTagName();
        if (TAG_CLIP_PATH.equals(tagName)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The Android Gradle Plugin image generator does not support `<clip-path>`");
        } else if ("gradient".equals(tagName)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The Android Gradle Plugin image generator does not support `<gradient>`");
        } else if (TAG_VECTOR.equals(tagName)) {
            Attr autoMirrored = element.getAttributeNodeNS(ANDROID_URI, ATTR_AUTO_MIRRORED);
            if (autoMirrored != null && "true".equals(autoMirrored.getValue())) {
                context.report(ISSUE, autoMirrored, context.getLocation(autoMirrored),
                        "The Android Gradle Plugin image generator does not support auto-mirrored");
            }
        } else if ("path".equals(tagName)) {
            Attr fillType = element.getAttributeNodeNS(ANDROID_URI, ATTR_FILL_TYPE);
            if (fillType != null && "evenOdd".equalsIgnoreCase(fillType.getValue())) {
                context.report(ISSUE, fillType, context.getLocation(fillType),
                        "The Android Gradle Plugin image generator does not support `evenOdd` fillType");
            }
        }
    }
}