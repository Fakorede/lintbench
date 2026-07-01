package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class VectorDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is "
                    + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` "
                    + "and bitmap images are generated for different screen resolutions for "
                    + "backwards compatibility.\n\n"
                    + "However, there are some limitations to this raster image generation, and "
                    + "this lint check flags elements and attributes that are not fully supported. "
                    + "You should manually check whether the generated output is acceptable for "
                    + "those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_HEIGHT = "height";
    private static final String ATTR_FILL_TYPE = "fillType";

    private static final String CLIP_PATH_MESSAGE =
            "The `<clip-path>` element is not supported when rasterizing vector drawables for older devices.";
    private static final String GRADIENT_MESSAGE =
            "The `<gradient>` element is not supported when rasterizing vector drawables for older devices.";
    private static final String FILL_TYPE_MESSAGE =
            "The `android:fillType` attribute is not supported when rasterizing vector drawables for older devices.";
    private static final String LARGE_ICON_MESSAGE =
            "This vector drawable has a very large width/height. When rasterized for older "
                    + "devices, this can lead to OutOfMemoryErrors. Consider reducing the "
                    + "width/height.";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, TAG_CLIP_PATH, TAG_GRADIENT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isVectorDrawable(context)) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_VECTOR.equals(tag)) {
            if (context.getProject().getMinSdk() < 21) {
                checkSize(context, element, ATTR_WIDTH);
                checkSize(context, element, ATTR_HEIGHT);
            }
        } else if (TAG_CLIP_PATH.equals(tag)) {
            if (context.getProject().getMinSdk() < 24) {
                context.report(ISSUE, element, context.getLocation(element), CLIP_PATH_MESSAGE);
            }
        } else if (TAG_GRADIENT.equals(tag)) {
            if (context.getProject().getMinSdk() < 24) {
                context.report(ISSUE, element, context.getLocation(element), GRADIENT_MESSAGE);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_FILL_TYPE);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!isVectorDrawable(context)) {
            return;
        }
        if (context.getProject().getMinSdk() >= 24) {
            return;
        }
        if (ATTR_FILL_TYPE.equals(attribute.getLocalName())) {
            context.report(ISSUE, attribute, context.getLocation(attribute), FILL_TYPE_MESSAGE);
        }
    }

    private static boolean isVectorDrawable(XmlContext context) {
        Element root = context.document.getDocumentElement();
        return root != null
                && TAG_VECTOR.equals(root.getTagName())
                && !isInVersionedFolder(context);
    }

    private static boolean isInVersionedFolder(XmlContext context) {
        File parent = context.file.getParentFile();
        if (parent == null) {
            return false;
        }
        String name = parent.getName();
        for (int i = 0; i < name.length() - 1; i++) {
            if (name.charAt(i) == '-' && name.charAt(i + 1) == 'v') {
                int j = i + 2;
                while (j < name.length() && Character.isDigit(name.charAt(j))) {
                    j++;
                }
                if (j > i + 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkSize(XmlContext context, Element element, String attrName) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
        if (attr == null) {
            return;
        }
        String value = attr.getValue().trim();
        if (value.endsWith("dp")) {
            try {
                float size = Float.parseFloat(value.substring(0, value.length() - 2));
                if (size >= 200) {
                    context.report(ISSUE, attr, context.getLocation(attr), LARGE_ICON_MESSAGE);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}