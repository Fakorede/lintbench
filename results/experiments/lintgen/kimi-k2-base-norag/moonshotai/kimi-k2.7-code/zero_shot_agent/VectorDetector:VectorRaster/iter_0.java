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

    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_PATH = "path";

    private static final String ATTR_FILL_TYPE = "fillType";

    private static final Set<String> UNSUPPORTED_ATTRIBUTES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(ATTR_FILL_TYPE)));

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, "
                    + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 "
                    + "or higher is used, a vector drawable placed in the `drawable` folder is "
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
        return Arrays.asList(TAG_CLIP_PATH, TAG_PATH);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        if (!context.getMainProject().isGradleProject()) {
            return;
        }

        if (context.getMainProject().getMinSdk() >= 24) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_CLIP_PATH.equals(tag)) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "This tag is not supported in images generated from this vector icon for older devices."
            );
            return;
        }

        if (TAG_PATH.equals(tag)) {
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