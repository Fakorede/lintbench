package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Limitations of vector drawable raster image generation",
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
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE))
            .setEnabledByDefault(true);

    private static final int API_VECTOR = 21;
    private static final int API_GRADIENT_FILL_TYPE = 24;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isVector(context)) {
            return;
        }

        String tag = element.getLocalName();
        int minSdk = context.getProject().getMinSdk();

        if (SdkConstants.TAG_GRADIENT.equals(tag) && minSdk < API_GRADIENT_FILL_TYPE) {
            report(context, element,
                    "The `<gradient>` element is not supported when generating raster images "
                            + "for devices running API 23 and below; the generated bitmap may "
                            + "differ from the vector drawable.");
        } else if (SdkConstants.TAG_CLIP_PATH.equals(tag) && minSdk < API_VECTOR) {
            report(context, element,
                    "The `<clip-path>` element is not supported when generating raster images "
                            + "for devices running API 20 and below; the generated bitmap may "
                            + "differ from the vector drawable.");
        }
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!isVector(context)) {
            return;
        }

        if (SdkConstants.ATTR_FILL_TYPE.equals(attribute.getLocalName())) {
            int minSdk = context.getProject().getMinSdk();
            if (minSdk < API_GRADIENT_FILL_TYPE) {
                report(context, attribute,
                        "The `fillType` attribute is not supported when generating raster images "
                                + "for devices running API 23 and below; the generated bitmap may "
                                + "differ from the vector drawable.");
            }
        }
    }

    private static boolean isVector(XmlContext context) {
        Element root = context.document.getDocumentElement();
        return root != null && SdkConstants.TAG_VECTOR.equals(root.getLocalName());
    }

    private static void report(XmlContext context, Node scope, String message) {
        context.report(ISSUE, scope, context.getLocation(scope), message);
    }
}