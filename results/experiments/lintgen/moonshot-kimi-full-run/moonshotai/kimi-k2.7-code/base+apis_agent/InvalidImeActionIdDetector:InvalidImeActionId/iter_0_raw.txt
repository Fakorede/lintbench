package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_IME_ACTION_ID;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. "
                    + "It must be an integer constant, or an integer resource reference, "
                    + "as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return ImmutableList.of(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && url.type == ResourceType.ID) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Invalid `android:imeActionId`: must be an integer constant or integer "
                            + "resource reference, not an ID reference");
        }
    }
}