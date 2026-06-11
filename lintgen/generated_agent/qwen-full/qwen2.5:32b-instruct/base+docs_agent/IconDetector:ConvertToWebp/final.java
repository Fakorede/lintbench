package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ConvertToWebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1, it supports transparency and lossless conversion as well.",
            "Previously, launcher icons were required to be in the PNG format but that restriction is no longer there, so lint now flags these.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<Location> checkResourceFile(@NonNull XmlContext context) {
        Element element = context.getXmlDocument().getDocumentElement();
        if (element != null && "resources".equals(element.getNodeName())) {
            for (Element child : getAllChildren(element)) {
                String format = child.getAttribute("format");
                if ("png".equalsIgnoreCase(format)) {
                    return Collections.singletonList(context.getLocation(child));
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Element> getAllChildren(Element element) {
        NodeList nodes = element.getChildNodes();
        List<Element> children = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                children.add((Element) node);
            }
        }
        return children;
    }

    @Override
    public Issue getIssue() {
        return ISSUE;
    }
}