package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.sdklib.AndroidVersion;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when "
                    + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                    + "higher is used, a vector drawable placed in the `drawable` folder is "
                    + "automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and "
                    + "bitmap images are generated for different screen resolutions for backwards "
                    + "compatibility.\n\n"
                    + "However, there are some limitations to this raster image generation, and "
                    + "this lint check flags elements and attributes that are not fully supported. "
                    + "You should manually check whether the generated output is acceptable for "
                    + "those older devices.",
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
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }

        Project project = context.getProject();
        AndroidVersion minSdkVersion = project.getMinSdkVersion();
        int minSdk = minSdkVersion != null ? minSdkVersion.getFeatureLevel() : 1;

        if (minSdk >= 24) {
            return;
        }

        if (isUseSupportLibrary(project)) {
            return;
        }

        checkElement(context, root, minSdk);
    }

    private void checkElement(XmlContext context, Element element, int minSdk) {
        String tagName = element.getTagName();

        if ("clip-path".equals(tagName)) {
            if (minSdk < 21) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The Android Gradle plugin image generator does not support clip-paths"
                );
            }
        } else if ("gradient".equals(tagName)
                || "linearGradient".equals(tagName)
                || "radialGradient".equals(tagName)
                || "sweepGradient".equals(tagName)) {
            if (minSdk < 24) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The Android Gradle plugin image generator does not support gradients"
                );
            }
        }

        if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "fillType")) {
            if (minSdk < 21) {
                Attr attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "fillType");
                if (attr != null) {
                    context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "The Android Gradle plugin image generator does not support fillType"
                    );
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, minSdk);
            }
        }
    }

    private boolean isUseSupportLibrary(Project project) {
        if (!project.isGradleProject()) {
            return false;
        }
        try {
            Object variant = null;
            try {
                variant = project.getClass().getMethod("getCurrentVariant").invoke(project);
            } catch (NoSuchMethodException e) {
                try {
                    variant = project.getClass().getMethod("getVariant").invoke(project);
                } catch (NoSuchMethodException ex) {
                    // Ignore
                }
            }

            if (variant != null) {
                Object mergedFlavor = variant.getClass().getMethod("getMergedFlavor").invoke(variant);
                if (mergedFlavor != null) {
                    Object vectorDrawables = mergedFlavor.getClass().getMethod("getVectorDrawables").invoke(mergedFlavor);
                    if (vectorDrawables != null) {
                        Object useSupportLibrary = vectorDrawables.getClass().getMethod("getUseSupportLibrary").invoke(vectorDrawables);
                        if (useSupportLibrary instanceof Boolean) {
                            return (Boolean) useSupportLibrary;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Fallback in case of model API changes
        }
        return false;
    }
}