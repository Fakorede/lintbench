package com.android.tools.lint.checks;

import com.android.resources.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class VectorDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "VectorRaster",
                    "Vector Image Generation",
                    "Vector icons require API 21 or API 24 depending on the features they use. "
                            + "When `minSdkVersion` is less than 21 or 24 and the Android Gradle "
                            + "plugin version is 1.4 or higher, a vector drawable placed in the "
                            + "`drawable` folder is automatically moved to "
                            + "`drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images "
                            + "are generated for backwards compatibility.\n"
                            + "\n"
                            + "However, the raster image generator does not support all vector "
                            + "features. This check flags elements and attributes that are not "
                            + "fully supported so that you can verify the generated output on "
                            + "older devices.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String KEY_REQUIRES_API = "requiresApi";

    private static final String TAG_VECTOR = "vector";
    private static final String TAG_CLIP_PATH = "clip-path";
    private static final String TAG_GRADIENT = "gradient";

    private static final String ATTR_FILL_TYPE = "fillType";
    private static final String ATTR_STROKE_LINE_CAP = "strokeLineCap";
    private static final String ATTR_STROKE_LINE_JOIN = "strokeLineJoin";
    private static final String ATTR_STROKE_MITER_LIMIT = "strokeMiterLimit";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_VECTOR.equals(root.getTagName())) {
            return;
        }

        FolderConfiguration configuration = context.getFolderConfiguration();
        if (configuration != null && configuration.getApiLevel() >= 21) {
            return;
        }

        checkElement(context, root);
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, LintMap map) {
        int requiredApi = map.getInt(KEY_REQUIRES_API, 21);

        Project project = context.getMainProject();
        if (project.getMinSdk() >= requiredApi) {
            return false;
        }

        com.android.ide.common.repository.GradleVersion version = project.getGradlePluginVersion();
        return version != null && version.isAtLeast(1, 4, 0);
    }

    private void checkElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (TAG_CLIP_PATH.equals(tag)) {
            report(
                    context,
                    element,
                    "The `<clip-path>` element is not supported when this vector drawable is "
                            + "rasterized into a PNG image for older devices.",
                    21);
        } else if (TAG_GRADIENT.equals(tag)) {
            report(
                    context,
                    element,
                    "The `<gradient>` element is not supported when this vector drawable is "
                            + "rasterized into a PNG image for older devices.",
                    24);
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node node = attributes.item(i);
            if (!(node instanceof Attr)) {
                continue;
            }
            Attr attr = (Attr) node;
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
            }
            if (name == null) {
                continue;
            }

            if (ATTR_FILL_TYPE.equals(name)) {
                report(
                        context,
                        attr,
                        "The `fillType` attribute is not supported when this vector drawable is "
                                + "rasterized into a PNG image for older devices.",
                        24);
            } else if (ATTR_STROKE_LINE_CAP.equals(name)
                    || ATTR_STROKE_LINE_JOIN.equals(name)
                    || ATTR_STROKE_MITER_LIMIT.equals(name)) {
                report(
                        context,
                        attr,
                        "The `" + name + "` attribute is not supported when this vector drawable "
                                + "is rasterized into a PNG image for older devices.",
                        21);
            }
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child instanceof Element) {
                checkElement(context, (Element) child);
            }
            child = child.getNextSibling();
        }
    }

    private void report(
            XmlContext context, Node node, String message, int requiredApi) {
        LintMap map = LintMap.create();
        map.put(KEY_REQUIRES_API, requiredApi);

        Location location = context.getLocation(node);
        Incident incident = new Incident(ISSUE, location, message);
        incident.setMap(map);
        context.report(incident);
    }
}