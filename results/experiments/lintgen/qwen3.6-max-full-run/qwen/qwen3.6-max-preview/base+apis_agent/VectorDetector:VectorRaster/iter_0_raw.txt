package com.android.tools.lint.checks;

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
import java.util.Collections;

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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_GRADIENT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isVectorDrawable(context)) {
            return;
        }
        context.report(ISSUE, element, context.getLocation(element),
                "Gradients are not fully supported in vector raster generation for older devices. " +
                "Manually verify the generated output.");
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_FILL_TYPE, ATTR_THEME);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!isVectorDrawable(context)) {
            return;
        }
        String name = attribute.getLocalName();
        String message;
        if (ATTR_FILL_TYPE.equals(name)) {
            message = "`fillType` is not fully supported in vector raster generation for older devices. " +
                      "Manually verify the generated output.";
        } else if (ATTR_THEME.equals(name)) {
            message = "`theme` references are not fully supported in vector raster generation for older devices. " +
                      "Manually verify the generated output.";
        } else {
            return;
        }
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    private static boolean isVectorDrawable(XmlContext context) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return false;
        }
        Element root = context.getDocument().getDocumentElement();
        return root != null && TAG_VECTOR.equals(root.getTagName());
    }
}