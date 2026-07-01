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
        int folderVersion = getFolderVersion(context);
        if (folderVersion >= 24) {
            return;
        }

        if (!isRasterGenerationSupported(context)) {
            return;
        }

        int minSdk = getMinSdk(context);
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

    private int getFolderVersion(XmlContext context) {
        try {
            return context.getFolderVersion();
        } catch (Throwable t) {
            return -1;
        }
    }

    private boolean isRasterGenerationSupported(XmlContext context) {
        Object project = context.getProject();
        if (project == null) {
            return true;
        }
        
        // 1. Try project.getSupportLibVectorDrawables() via reflection
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getSupportLibVectorDrawables");
            Boolean supportLib = (Boolean) method.invoke(project);
            if (supportLib != null) {
                return !supportLib;
            }
        } catch (Throwable t) {
            // ignore
        }

        // 2. Try Gradle model via reflection: project.getCurrentVariant().getMergedFlavor().getVectorDrawables().getUseSupportLibrary()
        try {
            java.lang.reflect.Method getVariantMethod = project.getClass().getMethod("getCurrentVariant");
            Object variant = getVariantMethod.invoke(project);
            if (variant != null) {
                java.lang.reflect.Method getMergedFlavorMethod = variant.getClass().getMethod("getMergedFlavor");
                Object mergedFlavor = getMergedFlavorMethod.invoke(variant);
                if (mergedFlavor != null) {
                    java.lang.reflect.Method getVectorDrawablesMethod = mergedFlavor.getClass().getMethod("getVectorDrawables");
                    Object vectorDrawables = getVectorDrawablesMethod.invoke(mergedFlavor);
                    if (vectorDrawables != null) {
                        java.lang.reflect.Method getUseSupportLibraryMethod = vectorDrawables.getClass().getMethod("getUseSupportLibrary");
                        Boolean useSupportLibrary = (Boolean) getUseSupportLibraryMethod.invoke(vectorDrawables);
                        if (useSupportLibrary != null) {
                            return !useSupportLibrary;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }

        return true;
    }

    private int getMinSdk(XmlContext context) {
        try {
            return context.getProject().getMinSdk();
        } catch (Throwable t) {
            try {
                Object minSdkVersion = context.getProject().getMinSdkVersion();
                if (minSdkVersion != null) {
                    try {
                        java.lang.reflect.Method getApiLevel = minSdkVersion.getClass().getMethod("getApiLevel");
                        return (Integer) getApiLevel.invoke(minSdkVersion);
                    } catch (Throwable t2) {
                        java.lang.reflect.Method getFeatureLevel = minSdkVersion.getClass().getMethod("getFeatureLevel");
                        return (Integer) getFeatureLevel.invoke(minSdkVersion);
                    }
                }
            } catch (Throwable t3) {
                // ignore
            }
        }
        return 1;
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
        if (element.hasAttributeNS(namespace, localName) || element.hasAttribute("android:" + localName)) {
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

    private String getAttributeValue(Element element, String namespace, String localName) {
        if (element.hasAttributeNS(namespace, localName)) {
            return element.getAttributeNS(namespace, localName);
        }
        return element.getAttribute("android:" + localName);
    }

    private void checkElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("vector".equals(tagName)) {
            String widthString = getAttributeValue(element, "http://schemas.android.com/apk/res/android", "width");
            String heightString = getAttributeValue(element, "http://schemas.android.com/apk/res/android", "height");
            double width = -1;
            double height = -1;
            if (widthString != null && !widthString.isEmpty()) {
                width = getDpValue(widthString);
            }
            if (heightString != null && !heightString.isEmpty()) {
                height = getDpValue(heightString);
            }
            if (width > 200 || height > 200) {
                context.report(
                        VECTOR_RASTER,
                        context.getNameLocation(element),
                        "Limit vector icons to 200x200 dp to avoid wasting memory when they are rasterized for older versions of the platform"
                );
            }
        } else if ("clip-path".equals(tagName)) {
            context.report(
                    VECTOR_RASTER,
                    context.getNameLocation(element),
                    "The Android Gradle plugin image generator does not support `<clip-path>`"
            );
        } else if ("gradient".equals(tagName)) {
            context.report(
                    VECTOR_RASTER,
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
                        context.getLocation(attr),
                        String.format("The Android Gradle plugin image generator does not support theme references (such as `%s`)", value)
                );
            }

            String localName = attr.getLocalName();
            String namespace = attr.getNamespaceURI();
            String name = attr.getName();
            if (("fillType".equals(localName) && "http://schemas.android.com/apk/res/android".equals(namespace))
                    || "android:fillType".equals(name)) {
                context.report(
                        VECTOR_RASTER,
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

    private static double getDpValue(String value) {
        if (value.endsWith("dp")) {
            return parseDouble(value, value.length() - 2);
        } else if (value.endsWith("dip")) {
            return parseDouble(value, value.length() - 3);
        }
        return -1;
    }

    private static double parseDouble(String value, int end) {
        try {
            return Double.parseDouble(value.substring(0, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}