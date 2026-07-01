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
import org.w3c.dom.Document;

public class VectorDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";
    private static final String ATTR_AUTO_MIRRORED = "autoMirrored";
    private static final String ATTR_FILL_TYPE = "fillType";

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on used features, "
                            + "but when `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or "
                            + "higher is used, a vector drawable placed in the `drawable` folder is automatically "
                            + "moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are "
                            + "generated for different screen resolutions for backwards compatibility.\n\n"
                            + "However, there are some limitations to this raster image generation, and this "
                            + "lint check flags elements and attributes that are not fully supported. "
                            + "You should manually check whether the generated output is acceptable for those "
                            + "older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        org.w3c.dom.Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals(TAG_VECTOR)) {
            return;
        }
        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if (TAG_CLIP_PATH.equals(tagName)) {
            Incident incident = new Incident(ISSUE, element, context.getNameLocation(element),
                    "This tag is not supported by the backwards-compatibility vector-to-raster generator",
                    null);
            LintMap map = new LintMap();
            map.put("minSdk", 21);
            incident.setMap(map);
            context.report(incident);
        } else if (TAG_GRADIENT.equals(tagName)) {
            Incident incident = new Incident(ISSUE, element, context.getNameLocation(element),
                    "This tag is not supported by the backwards-compatibility vector-to-raster generator",
                    null);
            LintMap map = new LintMap();
            map.put("minSdk", 24);
            incident.setMap(map);
            context.report(incident);
        } else if (TAG_PATH.equals(tagName)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_FILL_TYPE)) {
                org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_FILL_TYPE);
                Incident incident = new Incident(ISSUE, attribute, context.getLocation(attribute),
                        "This attribute is not supported by the backwards-compatibility vector-to-raster generator",
                        null);
                LintMap map = new LintMap();
                map.put("minSdk", 24);
                incident.setMap(map);
                context.report(incident);
            }
        } else if (TAG_VECTOR.equals(tagName)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_AUTO_MIRRORED)) {
                org.w3c.dom.Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_AUTO_MIRRORED);
                Incident incident = new Incident(ISSUE, attribute, context.getLocation(attribute),
                        "This attribute is not supported by the backwards-compatibility vector-to-raster generator",
                        null);
                LintMap map = new LintMap();
                map.put("minSdk", 21);
                incident.setMap(map);
                context.report(incident);
            }
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, (org.w3c.dom.Element) child);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        int minSdkThreshold = map.getInt("minSdk", 21);
        if (context.getProject().getMinSdk() >= minSdkThreshold) {
            return false;
        }
        Boolean useSupportLibrary = context.getProject().getVectorDrawablesUseSupportLibrary();
        if (useSupportLibrary != null && useSupportLibrary) {
            return false;
        }
        return true;
    }
}