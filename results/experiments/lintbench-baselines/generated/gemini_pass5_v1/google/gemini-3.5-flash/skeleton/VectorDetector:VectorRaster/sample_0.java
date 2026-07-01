package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle "
                            + "plugin 1.4 or higher is used, a vector drawable placed in the "
                            + "`drawable` folder is automatically moved to `drawable-anydpi-v21` "
                            + "or `drawable-anydpi-v24` and bitmap images are generated for "
                            + "different screen resolutions for backwards compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, "
                            + "and this lint check flags elements and attributes that are not "
                            + "fully supported. You should manually check whether the generated "
                            + "output is acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }

        int minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        if (minSdk >= 24) {
            return;
        }

        int folderVersion = context.getFolderVersion();
        if (folderVersion < 0) {
            folderVersion = 1;
        }

        if (folderVersion >= 24) {
            return;
        }

        checkElement(context, root, minSdk, folderVersion);
    }

    private void checkElement(
            @NonNull XmlContext context,
            @NonNull Element element,
            int minSdk,
            int folderVersion) {
        String tagName = element.getTagName();

        if ("clip-path".equals(tagName)) {
            if (minSdk < 21 && folderVersion < 21) {
                report(context, element, "The vector rasterizer does not support `<clip-path>`");
            }
        } else if ("gradient".equals(tagName)) {
            if (minSdk < 24 && folderVersion < 24) {
                report(context, element, "The vector rasterizer does not support `<gradient>`");
            }
        }

        if (hasAndroidAttribute(element, "autoMirrored")) {
            String autoMirrored = getAndroidAttribute(element, "autoMirrored");
            if ("true".equals(autoMirrored)) {
                if (minSdk < 21 && folderVersion < 21) {
                    Attr attribute = element.getAttributeNodeNS(ANDROID_URI, "autoMirrored");
                    if (attribute == null) {
                        attribute = element.getAttributeNode("android:autoMirrored");
                    }
                    report(
                            context,
                            attribute != null ? attribute : element,
                            "The vector rasterizer does not support `autoMirrored`");
                }
            }
        }

        if (hasAndroidAttribute(element, "fillType")) {
            if (minSdk < 24 && folderVersion < 24) {
                Attr attribute = element.getAttributeNodeNS(ANDROID_URI, "fillType");
                if (attribute == null) {
                    attribute = element.getAttributeNode("android:fillType");
                }
                report(
                        context,
                        attribute != null ? attribute : element,
                        "The vector rasterizer does not support `fillType`");
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, minSdk, folderVersion);
            }
        }
    }

    private static boolean hasAndroidAttribute(Element element, String name) {
        return element.hasAttributeNS(ANDROID_URI, name) || element.hasAttribute("android:" + name);
    }

    private static String getAndroidAttribute(Element element, String name) {
        if (element.hasAttributeNS(ANDROID_URI, name)) {
            return element.getAttributeNS(ANDROID_URI, name);
        }
        return element.getAttribute("android:" + name);
    }

    private void report(
            @NonNull XmlContext context,
            @NonNull Node node,
            @NonNull String message) {
        Incident incident = new Incident(ISSUE, message, context.getLocation(node));
        context.report(incident);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        Boolean depends = context.getProject().getDependsOnVectorDrawablesCompat();
        if (Boolean.TRUE.equals(depends)) {
            return false;
        }
        return true;
    }
}