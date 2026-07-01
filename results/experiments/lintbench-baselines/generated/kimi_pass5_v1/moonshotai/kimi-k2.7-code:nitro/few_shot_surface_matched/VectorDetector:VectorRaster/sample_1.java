package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_FILL_TYPE;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_GRADIENT;
import static com.android.SdkConstants.TAG_VECTOR;
import static com.android.SdkConstants.VALUE_EVEN_ODD;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintDriver;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin "
                            + "1.4 or higher is used, a vector drawable placed in the `drawable` "
                            + "folder is automatically moved to `drawable-anydpi-v21` or "
                            + "`drawable-anydpi-v24` and bitmap images are generated for "
                            + "different screen resolutions for backwards compatibility.\n\n"
                            + "However, there are some limitations to this raster image "
                            + "generation, and this lint check flags elements and attributes "
                            + "that are not fully supported. You should manually check whether "
                            + "the generated output is acceptable for those older devices.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return folderType == ResourceFolderType.DRAWABLE && fileName.endsWith(DOT_XML);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        int minSdk = context.getMainProject().getMinSdkVersion().getApiLevel();
        checkElement(context, root, minSdk);
    }

    @Override
    public boolean filterIncident(
            @NonNull Incident incident,
            @NonNull LintDriver driver,
            @NonNull Object scope,
            @NonNull Node scopeNode,
            @NonNull Node incidentNode) {
        // Only flag vectors in the default drawable folder; vectors already placed in a
        // version- or density-qualified folder are not auto-rasterized in the same way.
        Location location = incident.getLocation();
        if (location != null) {
            File file = location.getFile();
            if (file != null) {
                File parent = file.getParentFile();
                if (parent != null && !"drawable".equals(parent.getName())) {
                    return false;
                }
            }
        }

        return true;
    }

    private void checkElement(
            @NonNull XmlContext context, @NonNull Element element, int minSdk) {
        String tag = element.getTagName();

        if (TAG_GRADIENT.equals(tag) && minSdk < 24) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<gradient>` is not supported when a vector drawable is rasterized for "
                            + "devices running API 23 and earlier");
        } else if (TAG_CLIP_PATH.equals(tag) && minSdk < 21) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`<clip-path>` is not supported when a vector drawable is rasterized for "
                            + "devices running API 20 and earlier");
        }

        Attr fillType = element.getAttributeNodeNS(ANDROID_URI, ATTR_FILL_TYPE);
        if (fillType != null
                && VALUE_EVEN_ODD.equals(fillType.getValue())
                && minSdk < 24) {
            context.report(
                    ISSUE,
                    fillType,
                    context.getLocation(fillType),
                    "`fillType=\"evenOdd\"` is not supported when a vector drawable is "
                            + "rasterized for devices running API 23 and earlier");
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, minSdk);
            }
        }
    }
}