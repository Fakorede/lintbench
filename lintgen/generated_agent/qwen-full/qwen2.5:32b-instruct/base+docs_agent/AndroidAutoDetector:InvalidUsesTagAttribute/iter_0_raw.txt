package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.flatten;
import com.google.common.collect.ImmutableList;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import java.util.List;

public class AndroidAutoDetector extends XmlScanner {
    private static final String AUTOMOTIVE_APP_TAG = "automotiveApp";
    private static final String USES_TAG = "uses";
    private static final String NAME_ATTRIBUTE = "name";

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of(AUTOMOTIVE_APP_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getTagName().equals(AUTOMOTIVE_APP_TAG)) {
            for (Element child : flatten.getChildren(element)) {
                if (child.getTagName().equals(USES_TAG)) {
                    Attr nameAttr = child.getAttributeNode(NAME_ATTRIBUTE);
                    if (nameAttr != null) {
                        String nameValue = nameAttr.getValue();
                        if (!isValidNameAttribute(nameValue)) {
                            context.report(this, element,
                                    context.getLocation(child),
                                    "The 'uses' element in <automotiveApp> should contain a valid value for the 'name' attribute. Valid values are 'media', 'notification', or 'sms'.",
                                    null);
                        }
                    } else {
                        // If name attribute is missing
                        context.report(this, element,
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

    @Override
    public int getIssueRegistryPriority() {
        return 400;
    }
}