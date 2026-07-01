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
        if (minSdk >= 24) {
            return;
        }

        checkElement(context, element, minSdk);
    }

    private void checkElement(XmlContext context, Element element, int minSdk) {
        String tagName = element.getTagName();

        if (minSdk < 24) {
            if ("gradient".equals(tagName)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource folder-based image generation does not support `<gradient>`");
                return;
            }
        }

        if (minSdk < 21) {
            if ("clip-path".equals(tagName)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource folder-based image generation does not support `<clip-path>`");
            }
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String value = attr.getNodeValue();

            if (minSdk < 21) {
                if (value != null && value.startsWith("?")) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Resource folder-based image generation does not support theme references (such as `" + value + "`)");
                }
                if ("autoMirrored".equals(attr.getLocalName()) && "http://schemas.android.com/apk/res/android".equals(attr.getNamespaceURI())) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Resource folder-based image generation does not support `android:autoMirrored`");
                }
            }

            if (minSdk < 24) {
                if ("fillType".equals(attr.getLocalName()) && "http://schemas.android.com/apk/res/android".equals(attr.getNamespaceURI())) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Resource folder-based image generation does not support `android:fillType`");
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

    private static Boolean getVectorDrawablesUseSupportLibrary(XmlContext context) {
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