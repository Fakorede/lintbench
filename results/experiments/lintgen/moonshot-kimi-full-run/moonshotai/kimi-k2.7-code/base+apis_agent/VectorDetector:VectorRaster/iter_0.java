package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

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

    private static final String VECTOR_TAG = "vector";
    private static final String CLIP_PATH_TAG = "clip-path";
    private static final String FILL_TYPE_ATTR = "fillType";

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isVectorDrawable(context)) {
            return;
        }
        if (context.getProject().getMinSdk() >= 24) {
            return;
        }
        String tag = element.getTagName();
        if (CLIP_PATH_TAG.equals(tag)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The `<clip-path>` element is not fully supported when rasterizing vector "
                            + "drawables for older devices; verify the generated output.");
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!isVectorDrawable(context)) {
            return;
        }
        if (context.getProject().getMinSdk() >= 24) {
            return;
        }
        String name = attribute.getLocalName();
        if (FILL_TYPE_ATTR.equals(name)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "The `android:fillType` attribute is not fully supported when rasterizing "
                            + "vector drawables for older devices; verify the generated output.");
        }
    }

    private static boolean isVectorDrawable(XmlContext context) {
        Element root = context.document.getDocumentElement();
        return root != null && VECTOR_TAG.equals(root.getTagName());
    }
}