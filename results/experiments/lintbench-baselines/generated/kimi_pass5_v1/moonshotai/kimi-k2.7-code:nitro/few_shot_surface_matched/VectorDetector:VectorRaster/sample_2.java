package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_FILL_TYPE;
import static com.android.SdkConstants.TAG_CLIP_PATH;
import static com.android.SdkConstants.TAG_MASK;
import static com.android.SdkConstants.TAG_PATH;
import static com.android.SdkConstants.TAG_VECTOR;
import static com.android.SdkConstants.VALUE_EVEN_ODD;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class VectorDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, but when "
                            + "`minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 "
                            + "or higher is used, a vector drawable placed in the `drawable` folder "
                            + "is automatically moved to `drawable-anydpi-v21` or "
                            + "`drawable-anydpi-v24` and bitmap images are generated for different "
                            + "screen resolutions for backwards compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, "
                            + "and this lint check flags elements and attributes that are not fully "
                            + "supported. You should manually check whether the generated output is "
                            + "acceptable for those older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        checkElement(context, root);
    }

    private static void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_CLIP_PATH.equals(tagName)) {
            report(
                    context,
                    element,
                    "`<clip-path>` is not supported when this vector drawable is rasterized for "
                            + "backwards compatibility; manually verify the generated bitmaps");
        } else if (TAG_MASK.equals(tagName)) {
            report(
                    context,
                    element,
                    "`<mask>` is not supported when this vector drawable is rasterized for "
                            + "backwards compatibility; manually verify the generated bitmaps");
        } else if (TAG_PATH.equals(tagName)) {
            String fillType = element.getAttributeNS(ANDROID_URI, ATTR_FILL_TYPE);
            if (VALUE_EVEN_ODD.equals(fillType)) {
                report(
                        context,
                        element,
                        "`fillType=\"evenOdd\"` requires API 24 and may not be correctly "
                                + "rasterized for older devices; manually verify the generated "
                                + "bitmaps");
            }
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
            child = child.getNextSibling();
        }
    }

    private static void report(
            @NonNull XmlContext context, @NonNull Element element, @NonNull String message) {
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        Project project = incident.getProject();
        if (project == null || !project.isGradleProject()) {
            return false;
        }

        if (incident.getLocation() != null) {
            File file = incident.getLocation().getFile();
            if (file != null) {
                File parent = file.getParentFile();
                if (parent != null) {
                    String folderName = parent.getName();
                    if (folderName.contains("-v21")
                            || folderName.contains("-v24")
                            || "drawable-anydpi".equals(folderName)) {
                        return false;
                    }
                }
            }
        }

        int minSdk = project.getMinSdkVersion();
        return minSdk < 24;
    }
}