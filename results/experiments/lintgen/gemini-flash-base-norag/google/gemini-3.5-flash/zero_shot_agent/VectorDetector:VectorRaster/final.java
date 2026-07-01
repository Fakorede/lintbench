package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class VectorDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
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
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "clip-path", "gradient", "path");
    }

    private Boolean getSupportVectorDrawables(Project project) {
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getSupportVectorDrawables");
            return (Boolean) method.invoke(project);
        } catch (Throwable t) {
            // ignore
        }
        try {
            java.lang.reflect.Method getModel = project.getClass().getMethod("getGradleProjectModel");
            Object gradleProject = getModel.invoke(project);
            java.lang.reflect.Method getVariant = project.getClass().getMethod("getCurrentVariant");
            Object variant = getVariant.invoke(project);
            if (gradleProject != null && variant != null) {
                Object mergedFlavor = variant.getClass().getMethod("getMergedFlavor").invoke(variant);
                if (mergedFlavor != null) {
                    Object vectorDrawables = mergedFlavor.getClass().getMethod("getVectorDrawables").invoke(mergedFlavor);
                    if (vectorDrawables != null) {
                        return (Boolean) vectorDrawables.getClass().getMethod("getUseSupportLibrary").invoke(vectorDrawables);
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        Project project = context.getProject();
        Boolean useSupport = getSupportVectorDrawables(project);
        if (useSupport != null && useSupport) {
            return;
        }

        int minSdk = 1;
        if (project.getMinSdkVersion() != null) {
            minSdk = project.getMinSdkVersion().getFeatureLevel();
        }

        int folderVersion = context.getFolderVersion();
        if (folderVersion < 0) {
            folderVersion = 0;
        }

        String tagName = element.getTagName();
        boolean isPreLollipop = minSdk < 21 && folderVersion < 21;
        boolean isPreN = minSdk < 24 && folderVersion < 24;

        if (tagName.equals("clip-path")) {
            if (isPreLollipop) {
                context.report(ISSUE, element, context.getNameLocation(element),
                        "The raster image generator does not support `<clip-path>`");
            }
        } else if (tagName.equals("gradient")) {
            if (isPreN) {
                context.report(ISSUE, element, context.getNameLocation(element),
                        "The raster image generator does not support `<gradient>`");
            }
        } else if (tagName.equals("vector")) {
            if (isPreLollipop && element.hasAttributeNS(ANDROID_URI, "autoMirrored")) {
                Attr attr = element.getAttributeNodeNS(ANDROID_URI, "autoMirrored");
                context.report(ISSUE, attr, context.getLocation(attr),
                        "The raster image generator does not support `android:autoMirrored`");
            }
            if (isPreLollipop) {
                checkThemeReferences(context, element);
                checkSize(context, element);
            }
        } else if (tagName.equals("path")) {
            if (isPreN && element.hasAttributeNS(ANDROID_URI, "fillType")) {
                Attr attr = element.getAttributeNodeNS(ANDROID_URI, "fillType");
                context.report(ISSUE, attr, context.getLocation(attr),
                        "The raster image generator does not support `android:fillType`");
            }
            if (isPreLollipop) {
                checkThemeReferences(context, element);
            }
        }
    }

    private void checkSize(XmlContext context, Element element) {
        Attr widthAttr = element.getAttributeNodeNS(ANDROID_URI, "width");
        if (widthAttr != null) {
            String widthString = widthAttr.getValue();
            double width = getDpValue(widthString);
            if (width > 200) {
                context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                        "Limit vector drawables to 200x200 dp to avoid taking up too much memory when rasterized");
            }
        }
        Attr heightAttr = element.getAttributeNodeNS(ANDROID_URI, "height");
        if (heightAttr != null) {
            String heightString = heightAttr.getValue();
            double height = getDpValue(heightString);
            if (height > 200) {
                context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                        "Limit vector drawables to 200x200 dp to avoid taking up too much memory when rasterized");
            }
        }
    }

    private static double getDpValue(String value) {
        if (value == null) {
            return 0;
        }
        if (value.endsWith("dp")) {
            return parseDouble(value.substring(0, value.length() - 2));
        } else if (value.endsWith("dip")) {
            return parseDouble(value.substring(0, value.length() - 3));
        }
        return 0;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void checkThemeReferences(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                String value = attr.getValue();
                if (value != null && value.startsWith("?")) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "The raster image generator does not support theme references (such as " + value + ")");
                }
            }
        }
    }
}