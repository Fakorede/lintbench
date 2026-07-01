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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class VectorDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, " +
            "but when minSdkVersion is less than 21 or 24 and Android Gradle plugin 1.4 or " +
            "higher is used, a vector drawable placed in the drawable folder is automatically " +
            "moved to drawable-anydpi-v21 or drawable-anydpi-v24 and bitmap images are " +
            "generated for different screen resolutions for backwards compatibility.\n\n" +
            "However, there are some limitations to this raster image generation, and this " +
            "lint check flags elements and attributes that are not fully supported. " +
            "You should manually check whether the generated output is acceptable for those " +
            "older devices.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final List<String> API_21_ATTRS = Arrays.asList(
            "trimPathStart", "trimPathEnd", "trimPathOffset"
    );
    private static final List<String> API_24_ATTRS = Arrays.asList(
            "fillType", "gradient"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        String rootTag = root.getTagName();
        if (!rootTag.equals("vector") && !rootTag.equals("animated-vector")) {
            return;
        }

        int minSdk = context.getMinSdk();
        if (minSdk >= 24) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null || attributes.getLength() == 0) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String namespace = attr.getNamespaceURI();
            String localName = attr.getLocalName();
            String value = attr.getValue();

            if (SdkConstants.ANDROID_URI.equals(namespace)) {
                if (minSdk < 24 && API_24_ATTRS.contains(localName)) {
                    String msg = String.format(
                            "Attribute `%s` requires API level 24 or higher for full vector drawable support. " +
                            "When rasterized for older versions, the output may not be accurate.", localName);
                    context.report(ISSUE, element, context.getLocation(attr), msg);
                } else if (minSdk < 21 && API_21_ATTRS.contains(localName)) {
                    String msg = String.format(
                            "Attribute `%s` requires API level 21 or higher for full vector drawable support. " +
                            "When rasterized for older versions, the output may not be accurate.", localName);
                    context.report(ISSUE, element, context.getLocation(attr), msg);
                }
            }

            if (value != null && value.startsWith("?")) {
                String msg = "Theme references in vector drawables are not supported during " +
                        "raster image generation for older API levels.";
                context.report(ISSUE, element, context.getLocation(attr), msg);
            }
        }
    }
}