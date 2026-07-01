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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class VectorDetector extends Detector implements XmlScanner {

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
    private static final String TAG_GRADIENT = "gradient";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_THEME = "theme";
    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_HEIGHT = "height";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, TAG_GRADIENT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (TAG_GRADIENT.equals(tag)) {
            context.report(ISSUE, context.getLocation(element),
                    "Gradients are not fully supported in vector raster generation for older devices. " +
                    "Manually verify the generated output.");
        } else if (TAG_VECTOR.equals(tag)) {
            checkSize(context, element, ATTR_WIDTH);
            checkSize(context, element, ATTR_HEIGHT);
        }
    }

    private void checkSize(XmlContext context, Element element, String attrName) {
        Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, attrName);
        if (attr != null) {
            String value = attr.getValue();
            if (value != null && !value.isEmpty()) {
                float dp = parseDp(value);
                if (dp > 200f) {
                    context.report(ISSUE, context.getLocation(attr),
                            "Limit vector icons sizes to 200x200 dp otherwise they will be rasterized. " +
                            "Manually verify the generated output.");
                }
            }
        }
    }

    private float parseDp(String value) {
        value = value.trim();
        if (value.endsWith("dp")) {
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("dip")) {
            value = value.substring(0, value.length() - 3);
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_FILL_TYPE, ATTR_THEME);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }

        if (ATTR_FILL_TYPE.equals(name)) {
            context.report(ISSUE, context.getLocation(attribute),
                    "`fillType` is not fully supported in vector raster generation for older devices. " +
                    "Manually verify the generated output.");
        } else if (ATTR_THEME.equals(name)) {
            context.report(ISSUE, context.getLocation(attribute),
                    "`theme` references are not fully supported in vector raster generation for older devices. " +
                    "Manually verify the generated output.");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }
}