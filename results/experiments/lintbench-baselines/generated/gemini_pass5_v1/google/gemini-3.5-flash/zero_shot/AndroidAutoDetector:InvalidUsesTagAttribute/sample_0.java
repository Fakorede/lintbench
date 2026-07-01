package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends ResourceXmlDetector {

    public static final Issue INVALID_USES_TAG_ATTRIBUTE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for " +
            "the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
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
        Node parent = element.getParentNode();
        if (parent instanceof Element && "automotiveApp".equals(parent.getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr == null) {
                context.report(
                        INVALID_USES_TAG_ATTRIBUTE,
                        element,
                        context.getNameLocation(element),
                        "Missing `name` attribute for `<uses>` element"
                );
                return;
            }

            String value = nameAttr.getValue();
            if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
                context.report(
                        INVALID_USES_TAG_ATTRIBUTE,
                        nameAttr,
                        context.getValueLocation(nameAttr),
                        "Invalid `name` attribute: must be one of `media`, `notification`, or `sms`"
                );
            }
        }
    }
}