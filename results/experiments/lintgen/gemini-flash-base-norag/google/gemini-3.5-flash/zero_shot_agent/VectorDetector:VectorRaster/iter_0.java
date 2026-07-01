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

    @Override
    public void visitElement(XmlContext context, Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.DRAWABLE) {
            return;
        }

        String folderName = context.getFolder() != null ? context.getFolder().getName() : "";
        int folderVersion = 0;
        if (folderName.contains("-v")) {
            int index = folderName.indexOf("-v");
            if (index != -1) {
                String suffix = folderName.substring(index + 2);
                int end = 0;
                while (end < suffix.length() && Character.isDigit(suffix.charAt(end))) {
                    end++;
                }
                if (end > 0) {
                    try {
                        folderVersion = Integer.parseInt(suffix.substring(0, end));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }

        Project project = context.getProject();
        int minSdk = 1;
        if (project.getMinSdkVersion() != null) {
            minSdk = project.getMinSdkVersion().getFeatureLevel();
        }

        if (Boolean.TRUE.equals(project.getVectorDrawablesUseSupportLibrary())) {
            return;
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