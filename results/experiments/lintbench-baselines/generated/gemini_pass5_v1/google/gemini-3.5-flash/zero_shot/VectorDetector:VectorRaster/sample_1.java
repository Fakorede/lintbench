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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

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
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
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

        if (context.getResourceFolderType() != ResourceFolderType.DRAWABLE) {
            return;
        }

        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 21) {
            return;
        }

        if (context.getProject().getMinSdkVersion().getFeatureLevel() >= 21) {
            return;
        }

        if (isUseSupportLibrary(context)) {
            return;
        }

        checkElement(context, element);
    }

    private boolean isUseSupportLibrary(XmlContext context) {
        try {
            com.android.builder.model.AndroidProject model = context.getProject().getGradleProjectModel();
            if (model != null) {
                com.android.builder.model.Variant variant = context.getProject().getCurrentVariant();
                if (variant != null) {
                    com.android.builder.model.VectorDrawablesOptions options = variant.getMergedFlavor().getVectorDrawables();
                    if (options != null) {
                        Boolean use = options.getUseSupportLibrary();
                        return Boolean.TRUE.equals(use);
                    }
                }
            }
        } catch (Throwable t) {
            // Safely ignore if the classes/methods are missing in runtime
        }
        return false;
    }

    private void checkElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if ("clip-path".equals(tagName)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The vector rasterizer does not support `<clip-path>`");
        } else if ("gradient".equals(tagName)) {
            context.report(ISSUE, element, context.getNameLocation(element),
                    "The vector rasterizer does not support `<gradient>`");
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                String name = attribute.getLocalName();
                String namespace = attribute.getNamespaceURI();

                if (SdkConstants.ANDROID_URI.equals(namespace)) {
                    if ("autoMirrored".equals(name) && "true".equals(attribute.getValue())) {
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                "The vector rasterizer does not support `android:autoMirrored`");
                    } else if ("fillType".equals(name)) {
                        context.report(ISSUE, attribute, context.getLocation(attribute),
                                "The vector rasterizer does not support `android:fillType`");
                    }
                }

                String value = attribute.getValue();
                if (value != null && value.startsWith("?")) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "The vector rasterizer does not support theme references (e.g. " + value + ")");
                }
            }
        }

        NodeList children = element.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    checkElement(context, (Element) child);
                }
            }
        }
    }
}