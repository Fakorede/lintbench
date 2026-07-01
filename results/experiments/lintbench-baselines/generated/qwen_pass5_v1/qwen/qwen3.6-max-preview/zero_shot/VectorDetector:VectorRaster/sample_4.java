package com.android.tools.lint.checks;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;

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

    private static final List<String> RASTER_UNSUPPORTED_ATTRS = Arrays.asList(
            "fillType",
            "trimPathStart",
            "trimPathEnd",
            "trimPathOffset"
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("path");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Rasterization only occurs for resources without a v21+ qualifier
        if (context.getFolderVersion() >= 21) {
            return;
        }

        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"vector".equals(root.getTagName())) {
            return;
        }

        for (String attrName : RASTER_UNSUPPORTED_ATTRS) {
            Attr attr = element.getAttributeNodeNS(ANDROID_URI, attrName);
            if (attr != null) {
                String message = String.format(
                        "Vector images are rasterized for older devices. The attribute `%s` is not fully supported by the rasterizer.",
                        attrName
                );
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }
}