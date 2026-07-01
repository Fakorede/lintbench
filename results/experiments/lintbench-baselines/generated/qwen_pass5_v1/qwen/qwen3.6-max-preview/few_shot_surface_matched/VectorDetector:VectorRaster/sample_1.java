package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorRaster",
            "Vector Image Generation",
            "Vector icons require API 21 or API 24 depending on used features, but when minSdkVersion " +
                    "is less than 21 or 24 and Android Gradle plugin 1.4 or higher is used, a vector drawable " +
                    "placed in the drawable folder is automatically moved to drawable-anydpi-v21 or " +
                    "drawable-anydpi-v24 and bitmap images are generated for different screen resolutions for " +
                    "backwards compatibility.\n\nHowever, there are some limitations to this raster image " +
                    "generation, and this lint check flags elements and attributes that are not fully supported. " +
                    "You should manually check whether the generated output is acceptable for those older devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Set<String> UNSUPPORTED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fillType", "trimPathStart", "trimPathEnd", "trimPathOffset"
    ));

    private static final Set<String> UNSUPPORTED_ELEMENTS = new HashSet<>(Arrays.asList(
            "gradient", "aapt:attr"
    ));

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
        scanTree(context, root);
    }

    private void scanTree(@NonNull XmlContext context, @NonNull Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String tagName = element.getTagName();

            if (UNSUPPORTED_ELEMENTS.contains(tagName)) {
                if (filterIncident(context, element)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "This element is not fully supported by vector raster generation");
                }
            }

            for (String attr : UNSUPPORTED_ATTRIBUTES) {
                if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", attr)) {
                    if (filterIncident(context, element)) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "This attribute is not fully supported by vector raster generation");
                    }
                }
            }

            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                scanTree(context, children.item(i));
            }
        }
    }

    @Override
    public boolean filterIncident(@NonNull XmlContext context, @NonNull Element element) {
        return true;
    }
}