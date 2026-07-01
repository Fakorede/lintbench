package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

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
            6,
            Severity.WARNING,
            new Implementation(VectorDetector.class, EnumSet.of(Scope.RESOURCE_FILE)));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_VECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        // Rasterization for backwards compatibility only applies to unversioned 
        // drawable folders or those targeting below API 21
        if (context.getFolderVersion() >= 21) {
            return;
        }

        checkVectorChildren(context, element);
    }

    private void checkVectorChildren(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tag = childElement.getTagName();

                if (SdkConstants.TAG_GRADIENT.equals(tag)) {
                    context.report(ISSUE, childElement, context.getLocation(childElement),
                            "Gradients in vector drawables are not fully supported when generating " +
                            "raster images for older API levels");
                }

                String fillType = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_FILL_TYPE);
                if ("evenOdd".equals(fillType)) {
                    context.report(ISSUE, childElement, context.getLocation(childElement),
                            "android:fillType=\"evenOdd\" in vector drawables is not fully supported when generating " +
                            "raster images for older API levels");
                }

                checkVectorChildren(context, childElement);
            }
        }
    }
}