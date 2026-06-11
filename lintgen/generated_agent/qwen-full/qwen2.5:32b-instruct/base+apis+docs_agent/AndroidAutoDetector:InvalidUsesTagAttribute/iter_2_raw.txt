package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "InvalidUsesName",
            "Invalid value for 'name' attribute in <uses> element. Valid values are 'media', 'notification', or 'sms'.",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    true
            )
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("automotiveApp".equals(element.getParentNode().getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr != null) {
                String nameValue = nameAttr.getValue();
                if (!"media".equals(nameValue) && !"notification".equals(nameValue) && !"sms".equals(nameValue)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Invalid value for 'name' attribute in <uses> element. Valid values are 'media', 'notification', or 'sms'."
                    );
                }
            } else {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "'name' attribute is missing in <uses> element."
                );
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST.equals(folderType);
    }
}