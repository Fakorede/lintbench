package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VectorDetector extends ResourceXmlDetector implements Detector.XmlScanner {

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_PATH = "path";

    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_HEIGHT = "height";

    private static final Set<String> UNSUPPORTED_ATTRIBUTES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ATTR_FILL_TYPE)));

    private static final int MAX_DIMENSION_DP = 200;

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is "
                    + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and "
                    + "bitmap images are generated for different screen resolutions for backwards "
                    + "compatibility.\n\n"
                    + "However, there are some limitations to this raster image generation, and this "
                    + "lint check flags elements and attributes that are not fully supported. You "
                    + "should manually check whether the generated output is acceptable for those "
                    + "older devices.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_VECTOR, TAG_CLIP_PATH, TAG_PATH);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        if (!context.getMainProject().isGradleProject()) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_VECTOR.equals(tag)) {
            if (context.getMainProject().getMinSdk() < 21) {
                checkLargeIcon(context, element);
            }
        } else if (TAG_CLIP_PATH.equals(tag)) {
            if (context.getMainProject().getMinSdk() < 24) {
                context.report(
                        ISSUE,
                        context.getLocation(element),
                        "This tag is not supported in images generated from this vector icon for older devices."
                );
            }
        } else if (TAG_PATH.equals(tag)) {
            if (context.getMainProject().getMinSdk() < 24) {
                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr attr = (Attr) attributes.item(i);
                    String name = attr.getLocalName();
                    if (name == null) {
                        name = attr.getName();
                    }
                    if (UNSUPPORTED_ATTRIBUTES.contains(name)) {
                        context.report(
                                ISSUE,
                                context.getLocation(attr),
                                "This attribute is not supported in images generated from this vector icon for older devices."
                        );
                    }
                }
            }
        }
    }

    private static void checkLargeIcon(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        String widthValue = null;
        String heightValue = null;
        float widthDp = -1;
        float heightDp = -1;
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }
            if (ATTR_WIDTH.equals(name)) {
                widthValue = attr.getValue();
                widthDp = getSizeInDp(widthValue);
            } else if (ATTR_HEIGHT.equals(name)) {
                heightValue = attr.getValue();
                heightDp = getSizeInDp(heightValue);
            }
        }

        if (widthDp > MAX_DIMENSION_DP || heightDp > MAX_DIMENSION_DP) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "This vector icon is very large ("
                            + (widthValue != null ? widthValue : "?")
                            + " x "
                            + (heightValue != null ? heightValue : "?")
                            + "), which will result in large generated PNGs. Consider reducing the size."
            );
        }
    }

    private static float getSizeInDp(String value) {
        if (value == null) {
            return -1;
        }
        int len = value.length();
        if (len > 2 && value.endsWith("dp")) {
            try {
                return Float.parseFloat(value.substring(0, len - 2).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}