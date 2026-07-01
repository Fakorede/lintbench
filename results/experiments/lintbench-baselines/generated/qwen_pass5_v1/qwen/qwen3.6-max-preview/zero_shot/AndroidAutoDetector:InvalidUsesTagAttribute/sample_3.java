package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetector extends ResourceXmlDetector {

    private static final Set<String> VALID_NAMES = new HashSet<>(Arrays.asList("media", "notification", "sms"));

    public static final Issue ISSUE = Issue.create(
        "InvalidUsesTagAttribute",
        "Invalid `name` attribute for `uses` element",
        "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. " +
        "Valid values are `media`, `notification`, or `sms`.\n\n" +
        "Reference: https://developer.android.com/training/auto/start/index.html#auto-metadata",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"automotiveApp".equals(root.getTagName())) {
            return;
        }

        Node nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String nameValue = nameAttr.getNodeValue();
        if (!VALID_NAMES.contains(nameValue)) {
            context.report(
                ISSUE,
                context.getLocation(nameAttr),
                String.format("Invalid `name` attribute value `%1$s`. Must be one of: media, notification, sms.", nameValue)
            );
        }
    }
}