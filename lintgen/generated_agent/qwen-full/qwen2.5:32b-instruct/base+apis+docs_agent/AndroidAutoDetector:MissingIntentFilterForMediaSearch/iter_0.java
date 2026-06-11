package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("activity");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getNodeName().equals("activity")) {
            boolean hasIntentFilter = false;
            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                Node childNode = element.getChildNodes().item(i);
                if (childNode instanceof Element && "intent-filter".equals(childNode.getNodeName())) {
                    Element intentFilterElement = (Element) childNode;
                    hasIntentFilter = checkAction(intentFilterElement, MEDIA_PLAY_FROM_SEARCH);
                    if (hasIntentFilter) break;
                }
            }

            if (!hasIntentFilter) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH"
                );
            }
        }
    }

    private boolean checkAction(Element intentFilterElement, String actionName) {
        for (int i = 0; i < intentFilterElement.getChildNodes().getLength(); i++) {
            Node childNode = intentFilterElement.getChildNodes().item(i);
            if (childNode instanceof Element && "action".equals(childNode.getNodeName())) {
                Attr nameAttr = ((Element) childNode).getAttributeNode("android:name");
                if (nameAttr != null && actionName.equals(nameAttr.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Issue ISSUE = Issue.create(
            "MissingMediaPlayFromSearchIntentFilter",
            "Missing intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH",
            "To support voice searches on Android Auto, you should also register an `intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );
}