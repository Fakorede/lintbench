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
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner {
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";
    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";
    private static final Collection<String> VALID_USES_TAG_NAMES = Sets.newHashSet(
            VALUE_MEDIA, VALUE_NOTIFICATION, VALUE_SMS);

    public static final Issue INVALID_USES_TAG_ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `<uses>` element",
            "The `<uses>` element in `<automotiveApp>` must contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo("https://developer.android.com/training/auto/start/index.html#auto-metadata");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_AUTOMOTIVE_APP.equals(element.getParentNode().getNodeName())) {
            return;
        }

        Attr attr = element.getAttributeNodeNS(null, ATTR_NAME);
        if (attr == null) {
            context.report(INVALID_USES_TAG_ISSUE, element, context.getLocation(element),
                    "Invalid `name` attribute for `<uses>` element. Valid values are `media`, `notification`, or `sms`.");
            return;
        }

        String name = attr.getValue();
        if (!VALID_USES_TAG_NAMES.contains(name)) {
            context.report(INVALID_USES_TAG_ISSUE, attr, context.getLocation(attr),
                    "Invalid `name` attribute for `<uses>` element. Valid values are `media`, `notification`, or `sms`.");
        }
    }
}