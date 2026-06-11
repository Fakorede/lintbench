package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.utils.Pair;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import java.util.List;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import com.google.common.collect.ImmutableList;

public class AndroidAutoDetector extends XmlScanner {
    private static final String AUTOMOTIVE_APP_TAG = "automotiveApp";
    private static final String USES_TAG = "uses";
    private static final String NAME_ATTRIBUTE = "name";

    public static final Issue ISSUE = Issue.create(
            "InvalidAndroidAutoUsesName",
            "The 'uses' element in <automotiveApp> should contain a valid value for the 'name' attribute. Valid values are 'media', 'notification', or 'sms'.",
            "The `<uses>` element within an `<automotiveApp>` tag must have a `name` attribute with one of the following values: `media`, `notification`, or `sms`. This ensures that your app is correctly configured for Android Auto.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE)
            )
    );

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of(AUTOMOTIVE_APP_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getTagName().equals(AUTOMOTIVE_APP_TAG)) {
            for (Element child : getChildren(context, element)) {
                if (child.getTagName().equals(USES_TAG)) {
                    Attr nameAttr = child.getAttributeNode(NAME_ATTRIBUTE);
                    if (nameAttr != null) {
                        String nameValue = nameAttr.getValue();
                        if (!isValidNameAttribute(nameValue)) {
                            context.report(ISSUE, child,
                                    context.getLocation(child),
                                    "The 'uses' element in <automotiveApp> should contain a valid value for the 'name' attribute. Valid values are 'media', 'notification', or 'sms'.",
                                    null);
                        }
                    } else {
                        // If name attribute is missing
                        context.report(ISSUE, child,
                                context.getLocation(child),
                                "The 'uses' element in <automotiveApp> should contain a valid value for the 'name' attribute. Valid values are 'media', 'notification', or 'sms'.",
                                null);
                    }
                }
            }
        }
    }

    private boolean isValidNameAttribute(@NonNull String nameValue) {
        return "media".equals(nameValue)
                || "notification".equals(nameValue)
                || "sms".equals(nameValue);
    }

    @NonNull
    private List<Element> getChildren(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        ImmutableList.Builder<Element> builder = new ImmutableList.Builder<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode instanceof Element) {
                builder.add((Element) childNode);
            }
        }
        return builder.build();
    }
}