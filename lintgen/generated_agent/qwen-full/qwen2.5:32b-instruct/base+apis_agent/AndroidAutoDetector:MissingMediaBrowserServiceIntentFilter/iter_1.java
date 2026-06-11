package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Automotive Media App requires an exported service that extends `android.service.media.MediaBrowserService` with an intent-filter for the action `android.media.browse.MediaBrowserService` to be able to browse and play media.",
            "To do this, add\n" +
                    "<pre>\n" +
                    "&lt;intent-filter&gt;\n" +
                    "    &lt;action android:name=\"android.media.browse.MediaBrowserService\" /&gt;\n" +
                    "&lt;/intent-filter&gt;\n" +
                    "</pre>\n" +
                    "to the service that extends `android.service.media.MediaBrowserService`.",
            "https://developer.android.com/training/auto/audio/index.html#config_manifest",
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String serviceName = element.getAttribute("android:name");

        if (serviceName != null && isMediaBrowserService(context, serviceName)) {
            boolean hasIntentFilter = false;
            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                org.w3c.dom.Node childNode = element.getChildNodes().item(i);
                if ("intent-filter".equals(childNode.getNodeName())) {
                    Element intentFilterElement = (Element) childNode;
                    for (int j = 0; j < intentFilterElement.getChildNodes().getLength(); j++) {
                        org.w3c.dom.Node actionNode = intentFilterElement.getChildNodes().item(j);
                        if ("action".equals(actionNode.getNodeName()) &&
                                "android.media.browse.MediaBrowserService".equals(((org.w3c.dom.Attr) actionNode).getValue())) {
                            hasIntentFilter = true;
                            break;
                        }
                    }
                }
            }

            if (!hasIntentFilter) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Missing intent-filter for android.media.browse.MediaBrowserService");
            }
        }
    }

    private boolean isMediaBrowserService(XmlContext context, String serviceName) {
        JavaContext javaContext = context.getJavaContext();
        UClass serviceClass = javaContext.findClass(serviceName);
        if (serviceClass != null) {
            return serviceClass.isInheriting("android.service.media.MediaBrowserService");
        }
        return false;
    }

}