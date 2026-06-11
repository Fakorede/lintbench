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

    public static final Issue ISSUE = Issue.create(
            "InvalidAndroidAutoUsesName",
            "The <uses> element in `<automotiveApp>` should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            "Ensure that the `name` attribute of the `<uses>` element is one of the allowed values: `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String AUTOMOTIVE_APP = "automotiveApp";
    private static final String USES_ELEMENT = "uses";
    private static final String NAME_ATTRIBUTE = "name";

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(USES_ELEMENT);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getTagName().equals(USES_ELEMENT)) {
            Attr nameAttr = element.getAttributeNode(NAME_ATTRIBUTE);
            if (nameAttr != null) {
                String nameValue = nameAttr.getValue();
                if (!isValidNameAttribute(nameValue)) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Invalid value for 'name' attribute in <uses> element. Valid values are 'media', 'notification', or 'sms'."
                    );
                }
            } else {
                // The name attribute is required
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "'name' attribute is missing from the <uses> element."
                );
            }
        }
    }

    private boolean isValidNameAttribute(String value) {
        return "media".equals(value)
                || "notification".equals(value)
                || "sms".equals(value);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}