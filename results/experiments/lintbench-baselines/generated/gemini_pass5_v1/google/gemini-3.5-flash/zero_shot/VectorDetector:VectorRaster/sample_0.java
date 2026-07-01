package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
            "higher is used, a vector drawable placed in the `drawable` folder is automatically " +
            "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n\n" +
            "However, there are some limitations to this raster image generation, and " +
            "this lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for " +
            "those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!"vector".equals(element.getTagName())) {
            return;
        }

        if (!isRasterizationActive(context)) {
            return;
        }

        checkElement(context, element);
    }

    private boolean isRasterizationActive(XmlContext context) {
        if (context.getFolderType() != ResourceFolderType.DRAWABLE) {
            return false;
        }

        String folderName = context.getFolder() != null ? context.getFolder().getName() : "";
        if (folderName.contains("-v21") || folderName.contains("-v22") || 
            folderName.contains("-v23") || folderName.contains("-v24")) {
            return false;
        }

        if (context.getProject().getMinSdkVersion() != null) {
            int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
            if (minSdk >= 21) {
                return false;
            }
        }

        Boolean useSupport = context.getProject().getSupportLibVectorDrawables();
        if (useSupport != null && useSupport) {
            return false;
        }

        return true;
    }

    private void checkElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if ("clip-path".equals(tagName)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The `<clip-path>` element is not supported by the offline image generator; " +
                    "placeholder images will be created without clipping");
        } else if ("gradient".equals(tagName) || "linearGradient".equals(tagName) ||
                   "radialGradient".equals(tagName) || "sweepGradient".equals(tagName)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "Gradients are not supported by the offline image generator; " +
                    "placeholder images will be created without gradients");
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            String name = attr.getLocalName();
            String namespace = attr.getNamespaceURI();

            if (value.startsWith("?")) {
                context.report(ISSUE, attr, context.getValueLocation(attr),
                        "Theme references are not supported by the offline image generator");
            }

            if ("http://schemas.android.com/apk/res/android".equals(namespace)) {
                if ("autoMirrored".equals(name) && "true".equals(value)) {
                    context.report(ISSUE, attr, context.getValueLocation(attr),
                            "The `android:autoMirrored` attribute is not supported by the offline image generator");
                } else if ("fillType".equals(name)) {
                    context.report(ISSUE, attr, context.getValueLocation(attr),
                            "The `android:fillType` attribute is not supported by the offline image generator");
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }
}