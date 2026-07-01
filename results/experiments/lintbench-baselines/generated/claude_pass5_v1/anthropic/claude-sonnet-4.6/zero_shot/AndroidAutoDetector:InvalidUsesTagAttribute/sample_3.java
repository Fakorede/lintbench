package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue INVALID_USES_TAG_ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the "
                    + "`name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/auto/start/index.html#auto-metadata");

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final Collection<String> VALID_USES_NAMES = Arrays.asList(
            "media", "notification", "sms"
    );

    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check that the parent element is automotiveApp
        if (element.getParentNode() == null) {
            return;
        }
        String parentTagName = element.getParentNode().getNodeName();
        if (!TAG_AUTOMOTIVE_APP.equals(parentTagName)) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            // Missing name attribute - report error
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required `name` attribute for `<uses>` element"
            );
            return;
        }

        String nameValue = nameAttr.getValue();
        if (!VALID_USES_NAMES.contains(nameValue)) {
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    nameAttr,
                    context.getLocation(nameAttr),
                    String.format(
                            "Invalid value for `name` attribute in `<uses>` element. "
                                    + "Valid values are `media`, `notification`, or `sms`, "
                                    + "but found `%1$s`",
                            nameValue
                    )
            );
        }
    }
}