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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends Detector implements XmlScanner {

    public static final Issue VECTOR_RASTER = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or " +
            "higher is used, a vector drawable placed in the `drawable` folder is automatically " +
            "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n" +
            "\n" +
            "However, there are some limitations to this raster image generation, and this " +
            "lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for those " +
            "older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    VectorDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int folderVersion = context.getFolderVersion();
        if (folderVersion >= 24) {
            return;
        }

        if (!isRasterGenerationSupported(context)) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        if (minSdk >= 24) {
            return;
        }

        if (folderVersion >= 21) {
            return;
        }

        boolean hasApi24Features = hasApi24Features(element);
        boolean willRasterize = minSdk < 21 || (minSdk < 24 && hasApi24Features);

        if (!willRasterize) {
            return;
        }

        checkElement(context, element);
    }

    private boolean isRasterGenerationSupported(XmlContext context) {
        try {
            com.android.builder.model.AndroidProject project = context.getProject().getGradleProjectModel();
            if (project != null) {
                com.android.builder.model.Variant variant = context.getProject().getBuildVariant();
                if (variant != null) {
                    com.android.builder.model.ProductFlavor flavor = variant.getMergedFlavor();
                    com.android.builder.model.VectorDrawablesOptions options = flavor.getVectorDrawables();
                    if (options != null && Boolean.TRUE.equals(options.getUseSupportLibrary())) {
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            // Fallback to true if Gradle model is not accessible or APIs changed
        }
        return true;
    }

    private boolean hasApi24Features(Element root) {
        if (hasElement(root, "gradient")) {
            return true;
        }
        if (hasAttribute(root, "http://schemas.android.com/apk/res/android", "fillType")) {
            return true;
        }
        return false;
    }

    private boolean hasElement(Element element, String tagName) {
        if (tagName.equals(element.getTagName())) {
            return true;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (hasElement((Element) child, tagName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAttribute(Element element, String namespace, String localName) {
        if (element.hasAttributeNS(namespace, localName)) {
            return true;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (hasAttribute((Element) child, namespace, localName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("clip-path".equals(tagName)) {
            context.report(
                    VECTOR_RASTER,
                    element,
                    context.getNameLocation(element),
                    "The Android Gradle plugin image generator does not support `<clip-path>`"
            );
        } else if ("gradient".equals(tagName)) {
            context.report(
                    VECTOR_RASTER,
                    element,
                    context.getNameLocation(element),
                    "The Android Gradle plugin image generator does not support `<gradient>`"
            );
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value != null && value.startsWith("?")) {
                context.report(
                        VECTOR_RASTER,
                        attr,
                        context.getLocation(attr),
                        String.format("The Android Gradle plugin image generator does not support theme references (such as `%s`)", value)
                );
            }

            String localName = attr.getLocalName();
            String namespace = attr.getNamespaceURI();
            if ("fillType".equals(localName) && "http://schemas.android.com/apk/res/android".equals(namespace)) {
                context.report(
                        VECTOR_RASTER,
                        attr,
                        context.getLocation(attr),
                        "The Android Gradle plugin image generator does not support `fillType`"
                );
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