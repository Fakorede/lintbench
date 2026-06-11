package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingMediaPlayFromSearchIntentFilter",
            "Missing intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH",
            "To support voice searches on Android Auto, you should also register an `intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Collections.emptySet(),
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("activity");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getNodeName().equals("activity")) {
            boolean hasMediaPlayFromSearchIntentFilter = false;

            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                Node childNode = element.getChildNodes().item(i);
                if (childNode instanceof Element && "intent-filter".equals(childNode.getNodeName())) {
                    for (int j = 0; j < ((Element) childNode).getChildNodes().getLength(); j++) {
                        Node actionNode = ((Element) childNode).getChildNodes().item(j);
                        if ("action".equals(actionNode.getNodeName()) && "name".equals(((org.w3c.dom.Attr) actionNode.getAttributes().getNamedItem("android:name")).getName())) {
                            String nameValue = ((org.w3c.dom.Attr) actionNode.getAttributes().getNamedItem("android:name")).getValue();
                            if (nameValue.equals("android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                                hasMediaPlayFromSearchIntentFilter = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (!hasMediaPlayFromSearchIntentFilter) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH"
                );
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST.equals(folderType);
    }
}