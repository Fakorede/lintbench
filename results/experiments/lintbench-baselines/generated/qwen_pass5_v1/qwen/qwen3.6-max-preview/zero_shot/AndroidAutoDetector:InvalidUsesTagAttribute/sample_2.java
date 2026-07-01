package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. " +
            "Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE || !"automotiveApp".equals(parent.getNodeName())) {
            return;
        }

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (!"media".equals(name) && !"notification".equals(name) && !"sms".equals(name)) {
            context.report(ISSUE, context.getLocation(nameAttr),
                    "Invalid value for `name` attribute: must be one of `media`, `notification`, or `sms`");
        }
    }
}