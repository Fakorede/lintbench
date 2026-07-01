package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

public class VectorDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when " +
            "minSdkVersion is less than 21 or 24 and Android Gradle plugin 1.4 or " +
            "higher is used, a vector drawable placed in the drawable folder is " +
            "automatically moved to drawable-anydpi-v21 or drawable-anydpi-v24 and " +
            "bitmap images are generated for different screen resolutions for backwards " +
            "compatibility.\n" +
            "\n" +
            "However, there are some limitations to this raster image generation, and " +
            "this lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for " +
            "those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Boolean useSupportLibrary = getVectorDrawablesUseSupportLibrary(context);
        if (Boolean.TRUE.equals(useSupportLibrary)) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        int folderVersion = getFolderVersion(context);
        int effectiveSdk = Math.max(minSdk, folderVersion);

        if (effectiveSdk >= 24) {
            return;
        }

        checkElement(context, element, effectiveSdk);
    }

    private void checkElement(XmlContext context, Element element, int effectiveSdk) {
        String tagName = element.getTagName();

        if (effectiveSdk < 24) {
            if ("gradient".equals(tagName)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource folder-based image generation does not support `<gradient>`");
                return;
            }
        }

        if (effectiveSdk < 21) {
            if ("clip-path".equals(tagName)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource folder-based image generation does not support `<clip-path>`");
            }
            if ("vector".equals(tagName)) {
                Attr widthAttr = getAttributeNode(element, "http://schemas.android.com/apk/res/android", "width");
                if (widthAttr != null) {
                    double width = getDpValue(widthAttr.getValue());
                    if (width > 200) {
                        context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                                "Limit vector icons to 200x200 dp to avoid wasting memory");
                    }
                }
                Attr heightAttr = getAttributeNode(element, "http://schemas.android.com/apk/res/android", "height");
                if (heightAttr != null) {
                    double height = getDpValue(heightAttr.getValue());
                    if (height > 200) {
                        context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                                "Limit vector icons to 200x200 dp to avoid wasting memory");
                    }
                }
            }
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (!(attr instanceof Attr)) {
                continue;
            }
            Attr attribute = (Attr) attr;
            String value = attribute.getValue();
            String localName = attribute.getLocalName();
            if (localName == null) {
                localName = attribute.getName();
                if (localName.contains(":")) {
                    localName = localName.substring(localName.indexOf(':') + 1);
                }
            }
            String namespace = attribute.getNamespaceURI();

            if (effectiveSdk < 21) {
                if (value != null && value.startsWith("?")) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Resource folder-based image generation does not support theme references (such as `" + value + "`)");
                }
                if ("autoMirrored".equals(localName) && ("http://schemas.android.com/apk/res/android".equals(namespace) || namespace == null)) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Resource folder-based image generation does not support `android:autoMirrored`");
                }
            }

            if (effectiveSdk < 24) {
                if ("fillType".equals(localName) && ("http://schemas.android.com/apk/res/android".equals(namespace) || namespace == null)) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Resource folder-based image generation does not support `android:fillType`");
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, effectiveSdk);
            }
        }
    }

    private static Attr getAttributeNode(Element element, String namespace, String localName) {
        Attr attr = element.getAttributeNodeNS(namespace, localName);
        if (attr == null) {
            attr = element.getAttributeNode("android:" + localName);
        }
        if (attr == null) {
            attr = element.getAttributeNode(localName);
        }
        return attr;
    }

    private static double getDpValue(String value) {
        if (value == null) {
            return -1;
        }
        value = value.trim();
        if (value.endsWith("dp")) {
            return parseDouble(value.substring(0, value.length() - 2));
        } else if (value.endsWith("dip")) {
            return parseDouble(value.substring(0, value.length() - 3));
        } else if (value.endsWith("px")) {
            return parseDouble(value.substring(0, value.length() - 2));
        }
        return -1;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int getFolderVersion(XmlContext context) {
        int folderVersion = -1;
        try {
            folderVersion = context.getFolderVersion();
        } catch (Throwable t) {
            // ignore
        }
        if (folderVersion <= 0) {
            java.io.File parentFile = context.file.getParentFile();
            if (parentFile != null) {
                String name = parentFile.getName();
                int index = name.indexOf("-v");
                if (index != -1) {
                    try {
                        folderVersion = Integer.parseInt(name.substring(index + 2));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }
        return folderVersion;
    }

    private static Boolean getVectorDrawablesUseSupportLibrary(XmlContext context) {
        try {
            return context.getProject().getSupportLibVectorDrawables();
        } catch (Throwable t) {
            // ignore
        }

        try {
            java.lang.reflect.Method method = context.getProject().getClass().getMethod("getVectorDrawablesUseSupportLibrary");
            return (Boolean) method.invoke(context.getProject());
        } catch (Throwable t) {
            // ignore
        }

        try {
            Object project = context.getProject();
            java.lang.reflect.Method getAndroidProject = project.getClass().getMethod("getAndroidProject");
            Object androidProject = getAndroidProject.invoke(project);
            if (androidProject != null) {
                java.lang.reflect.Method getCurrentVariant = project.getClass().getMethod("getCurrentVariant");
                Object variant = getCurrentVariant.invoke(project);
                if (variant != null) {
                    java.lang.reflect.Method getMergedFlavor = variant.getClass().getMethod("getMergedFlavor");
                    Object mergedFlavor = getMergedFlavor.invoke(variant);
                    if (mergedFlavor != null) {
                        java.lang.reflect.Method getVectorDrawablesOptions = mergedFlavor.getClass().getMethod("getVectorDrawablesOptions");
                        Object vectorDrawablesOptions = getVectorDrawablesOptions.invoke(mergedFlavor);
                        if (vectorDrawablesOptions != null) {
                            java.lang.reflect.Method getUseSupportLibrary = vectorDrawablesOptions.getClass().getMethod("getUseSupportLibrary");
                            return (Boolean) getUseSupportLibrary.invoke(vectorDrawablesOptions);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }

        return null;
    }
}