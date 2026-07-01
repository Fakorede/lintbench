package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class VectorDetector extends ResourceXmlDetector {
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_PATH = "path";
    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_WIDTH = "width";
    private static final String ATTR_HEIGHT = "height";
    private static final int MAX_DIMENSION_DP = 200;
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(dp|dip|px|sp|pt|in|mm)?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, "
                    + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle "
                    + "plugin 1.4 or higher is used, a vector drawable placed in the "
                    + "`drawable` folder is automatically moved to `drawable-anydpi-v21` "
                    + "or `drawable-anydpi-v24` and bitmap images are generated for "
                    + "different screen resolutions for backwards compatibility. "
                    + "However, there are some limitations to this raster image "
                    + "generation, and this lint check flags elements and attributes "
                    + "that are not fully supported. You should manually check whether "
                    + "the generated output is acceptable for those older devices.",
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

        Project project = context.getMainProject();
        if (!project.isGradleProject()) {
            return;
        }

        int minSdk = project.getMinSdk();
        String tag = element.getTagName();
        if (TAG_VECTOR.equals(tag)) {
            if (minSdk < 21) {
                checkLargeIcon(context, element);
            }
        } else if (TAG_CLIP_PATH.equals(tag)) {
            if (minSdk < 24) {
                context.report(ISSUE, context.getLocation(element),
                        "The `clip-path` element is not fully supported when "
                                + "generating raster images for older devices.");
            }
        } else if (TAG_PATH.equals(tag)) {
            if (minSdk < 24) {
                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Attr attr = (Attr) attributes.item(i);
                    String name = getAttributeName(attr);
                    if (ATTR_FILL_TYPE.equals(name)) {
                        context.report(ISSUE, context.getLocation(attr),
                                "The `android:fillType` attribute is not fully supported "
                                        + "when generating raster images for older devices.");
                    }
                }
            }
        }
    }

    private static void checkLargeIcon(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = getAttributeName(attr);
            if (ATTR_WIDTH.equals(name) || ATTR_HEIGHT.equals(name)) {
                String value = attr.getValue();
                if (value != null && getDimensionDp(value) > MAX_DIMENSION_DP) {
                    context.report(ISSUE, context.getLocation(attr),
                            "Raster images may be generated at this size for older "
                                    + "devices; a width/height of " + MAX_DIMENSION_DP
                                    + "dp or less is recommended.");
                }
            }
        }
    }

    private static String getAttributeName(Attr attr) {
        String name = attr.getLocalName();
        if (name == null) {
            name = attr.getName();
            int colon = name.indexOf(':');
            if (colon != -1) {
                name = name.substring(colon + 1);
            }
        }
        return name;
    }

    private static double getDimensionDp(String value) {
        Matcher matcher = DIMENSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return 0;
        }
        double number = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || unit.equalsIgnoreCase("dp") || unit.equalsIgnoreCase("dip")) {
            return number;
        }
        if (unit.equalsIgnoreCase("px")) {
            return number / 1.5;
        }
        return 0;
    }
}