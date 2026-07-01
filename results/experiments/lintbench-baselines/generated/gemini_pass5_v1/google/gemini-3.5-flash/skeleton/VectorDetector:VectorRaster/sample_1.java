package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.*;

public class VectorDetector extends ResourceXmlDetector {

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
        Element root = document.getDocumentElement();
        if (root == null || !root.getTagName().equals("vector")) {
            return;
        }

        if (context.getFolderVersion() >= 21) {
            return;
        }

        if (context.getProject().getMinSdkVersion().getFeatureLevel() >= 21) {
            return;
        }

        Boolean useSupportLibrary = context.getProject().getSupportLibVectorDrawables();
        if (useSupportLibrary != null && useSupportLibrary) {
            return;
        }

        checkElement(context, root);
    }

    private void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (tagName.equals("clip-path")) {
            Incident incident = new Incident(ISSUE);
            incident.setNode(element);
            incident.setLocation(context.getNameLocation(element));
            incident.setMessage("The vector-to-raster generator does not support `<clip-path>`");
            context.report(incident);
        } else if (tagName.equals("gradient")) {
            Incident incident = new Incident(ISSUE);
            incident.setNode(element);
            incident.setLocation(context.getNameLocation(element));
            incident.setMessage("The vector-to-raster generator does not support `<gradient>`");
            context.report(incident);
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                String name = attr.getLocalName();
                if (name == null) {
                    name = attr.getName();
                }
                String value = attr.getValue();
                String namespace = attr.getNamespaceURI();
                boolean isAndroid = "http://schemas.android.com/apk/res/android".equals(namespace)
                        || (namespace == null && attr.getName().startsWith("android:"));

                if (isAndroid) {
                    if ("fillType".equals(name)) {
                        Incident incident = new Incident(ISSUE);
                        incident.setNode(attr);
                        incident.setLocation(context.getLocation(attr));
                        incident.setMessage("The vector-to-raster generator does not support `android:fillType`");
                        context.report(incident);
                    } else if ("autoMirrored".equals(name)) {
                        Incident incident = new Incident(ISSUE);
                        incident.setNode(attr);
                        incident.setLocation(context.getLocation(attr));
                        incident.setMessage("The vector-to-raster generator does not support `android:autoMirrored`");
                        context.report(incident);
                    }
                }

                if (value != null && value.startsWith("?")) {
                    Incident incident = new Incident(ISSUE);
                    incident.setNode(attr);
                    incident.setLocation(context.getLocation(attr));
                    incident.setMessage("The vector-to-raster generator does not support theme references");
                    context.report(incident);
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }
}