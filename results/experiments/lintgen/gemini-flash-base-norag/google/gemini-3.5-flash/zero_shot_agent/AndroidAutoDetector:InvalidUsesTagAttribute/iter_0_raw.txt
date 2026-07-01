package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the " +
            "`name` attribute. Valid values are `media`, `notification`, or `sms`.",
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
        if (parent != null && "automotiveApp".equals(parent.getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr == null) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing `name` attribute for `uses` element"
                );
                return;
            }

            String nameValue = nameAttr.getValue();
            if (!"media".equals(nameValue) && !"notification".equals(nameValue) && !"sms".equals(nameValue)) {
                context.report(
                        ISSUE,
                        nameAttr,
                        context.getValueLocation(nameAttr),
                        "Invalid `name` attribute value. Valid values are `media`, `notification`, or `sms`."
                );
            }
        }
    }
}