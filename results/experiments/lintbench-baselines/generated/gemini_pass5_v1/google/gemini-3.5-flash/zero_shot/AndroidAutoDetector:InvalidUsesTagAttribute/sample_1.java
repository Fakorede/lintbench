package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    public static final Issue INVALID_USES_TAG_ATTRIBUTE = Issue.create(
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
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent != null && "automotiveApp".equals(parent.getNodeName())) {
            Attr nameAttr = element.getAttributeNode("name");
            if (nameAttr != null) {
                String value = nameAttr.getValue();
                if (!"media".equals(value) && !"notification".equals(value) && !"sms".equals(value)) {
                    context.report(
                            INVALID_USES_TAG_ATTRIBUTE,
                            nameAttr,
                            context.getLocation(nameAttr),
                            "Invalid value for `name` attribute: should be `media`, `notification`, or `sms`"
                    );
                }
            } else {
                context.report(
                        INVALID_USES_TAG_ATTRIBUTE,
                        element,
                        context.getNameLocation(element),
                        "Missing `name` attribute for `<uses>` element"
                );
            }
        }
    }
}