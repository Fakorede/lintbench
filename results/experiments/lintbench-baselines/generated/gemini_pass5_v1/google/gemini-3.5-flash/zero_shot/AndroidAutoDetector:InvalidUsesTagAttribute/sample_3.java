package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for " +
            "the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return;
        }
        Element root = document.getDocumentElement();
        if (root == null || !"automotiveApp".equals(root.getTagName())) {
            return;
        }

        Attr attr = element.getAttributeNode("name");
        if (attr == null) {
            attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "name");
        }

        if (attr == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `name` attribute for `uses` element"
            );
            return;
        }

        String value = attr.getValue();
        if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
            context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "Invalid value for `name` attribute. Valid values are `media`, `notification`, or `sms`."
            );
        }
    }
}