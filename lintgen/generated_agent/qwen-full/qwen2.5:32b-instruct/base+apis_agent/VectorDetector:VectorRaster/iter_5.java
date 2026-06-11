package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.xml.XmlUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class VectorDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorImageGeneration",
            "Checks for vector image generation limitations",
            "Vector icons require API 21 or API 24 depending on used features, but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or higher is used, a vector drawable placed in the `drawable` folder is automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are generated for different screen resolutions for backwards compatibility.\n\nHowever, there are some limitations to this raster image generation, and this lint check flags elements and attributes that are not fully supported. You should manually check whether the generated output is acceptable for those older devices.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(VectorDetector.class, EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("vector".equals(element.getTagName())) {
            // Check for unsupported elements or attributes here.
            // For example:
            List<Attr> attrs = getAttributesByName(element, "pathData");
            if (!attrs.isEmpty()) {
                context.report(ISSUE, element, context.getLocation(element),
                        "The 'pathData' attribute may not be fully supported in generated raster images.");
            }
        }
    }

    private List<Attr> getAttributesByName(Element element, String attributeName) {
        return XmlUtils.getAttributeNodes(element, attributeName);
    }
}