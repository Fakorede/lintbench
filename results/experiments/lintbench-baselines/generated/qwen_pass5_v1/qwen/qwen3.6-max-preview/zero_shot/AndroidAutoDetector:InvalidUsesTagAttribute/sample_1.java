package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. " +
            "Valid values are `media`, `notification`, or `sms`.\n" +
            "Reference: https://developer.android.com/training/auto/start/index.html#auto-metadata",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !"automotiveApp".equals(root.getTagName())) {
            return;
        }

        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `name` attribute for `<uses>` element");
            return;
        }

        String name = nameAttr.getValue();
        if (!"media".equals(name) && !"notification".equals(name) && !"sms".equals(name)) {
            context.report(ISSUE, nameAttr, context.getLocation(nameAttr),
                    "Invalid value for `name` attribute. Must be one of: media, notification, sms");
        }
    }
}