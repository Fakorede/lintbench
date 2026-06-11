package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;

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
        return List.of(AUTOMOTIVE_APP_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getTagName().equals(AUTOMOTIVE_APP_TAG)) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node childNode = children.item(i);
                if (childNode instanceof Element && ((Element) childNode).getTagName().equals(USES_TAG)) {
                    Element child = (Element) childNode;
                    String nameAttrValue = child.getAttribute(NAME_ATTRIBUTE);
                    if (!isValidNameAttribute(nameAttrValue)) {
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
}