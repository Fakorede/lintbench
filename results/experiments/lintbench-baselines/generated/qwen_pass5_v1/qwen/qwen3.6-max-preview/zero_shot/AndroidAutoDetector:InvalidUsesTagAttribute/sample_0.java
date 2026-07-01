package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

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

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        Node parent = element.getParentNode();
        if (parent != null && "automotiveApp".equals(parent.getNodeName())) {
            String name = element.getAttribute("name");
            if (!name.isEmpty() && !VALID_NAMES.contains(name)) {
                context.report(
                        ISSUE,
                        context.getLocation(element.getAttributeNode("name")),
                        "Invalid value for `name` attribute. Must be one of: media, notification, sms"
                );
            }
        }
    }
}