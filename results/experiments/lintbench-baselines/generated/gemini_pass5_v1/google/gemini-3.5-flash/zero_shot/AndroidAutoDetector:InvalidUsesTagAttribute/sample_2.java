package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. " +
            "Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/auto/start/index.html#auto-metadata");

    public AndroidAutoDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.XML) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !"automotiveApp".equals(parent.getNodeName())) {
            return;
        }

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "The `<uses>` element must define a `name` attribute"
            );
            return;
        }

        String value = nameAttr.getValue();
        if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
            context.report(
                    ISSUE,
                    nameAttr,
                    context.getValueLocation(nameAttr),
                    "Invalid `name` attribute value. Valid values are `media`, `notification`, or `sms`."
            );
        }
    }
}