package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class InvalidImeActionIdDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "InvalidImeActionId",
            "Invalid imeActionId declaration",
            "`android:imeActionId` should not be a resource ID such as `@+id/resName`. " +
            "It must be an integer constant, or an integer resource reference, as defined in `EditorInfo`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    InvalidImeActionIdDetector.class,
                    Scope.LAYOUT_RESOURCE_FILES
            )
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value.startsWith("@id/") || 
            value.startsWith("@+id/") || 
            value.startsWith("@android:id/") || 
            value.startsWith("@*android:id/")) {
            
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`android:imeActionId` should be an integer constant or an integer resource reference, not a resource ID"
            );
        }
    }
}