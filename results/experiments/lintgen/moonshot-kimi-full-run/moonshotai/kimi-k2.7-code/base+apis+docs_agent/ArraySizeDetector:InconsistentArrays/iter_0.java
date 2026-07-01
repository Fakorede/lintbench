package com.android.tools.lint.checks;

import androidx.annotation.NonNull;

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArraySizeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have the same "
                    + "number of elements as the original array. When adding or removing elements "
                    + "to an array, it is easy to forget to update all the locales.\n"
                    + "\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration, so use your own "
                    + "judgment to decide if this is really an error. You can suppress this error "
                    + "type if it finds false errors in your project.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String TAG_ARRAY = "array";
    private static final String TAG_STRING_ARRAY = "string-array";
    private static final String TAG_INTEGER_ARRAY = "integer-array";
    private static final String TAG_ITEM = "item";

    private final Map<String, ArrayInfo> mArrays = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        int size = countItems(element);
        String configuration = getConfigurationName(context);

        ArrayInfo info = mArrays.get(name);
        if (info == null) {
            info = new ArrayInfo(name);
            mArrays.put(name, info);
        }
        info.variants.add(new Variant(configuration, size,
                context.createLocationHandle(element)));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ArrayInfo info : mArrays.values()) {
            if (info.variants.isEmpty()) {
                continue;
            }

            Variant defaultVariant = null;
            for (Variant variant : info.variants) {
                if (variant.configuration.isEmpty()) {
                    defaultVariant = variant;
                    break;
                }
            }

            if (defaultVariant != null) {
                for (Variant variant : info.variants) {
                    if (variant == defaultVariant) {
                        continue;
                    }
                    if (variant.size != defaultVariant.size) {
                        String message = String.format(Locale.US,
                                "Array size mismatch: %1$d items in %2$s, %3$d in default",
                                variant.size,
                                variant.configuration.isEmpty() ? "default" : variant.configuration,
                                defaultVariant.size);
                        context.report(ISSUE, variant.handle.resolve(), message);
                    }
                }
            } else {
                Variant reference = info.variants.get(0);
                for (int i = 1; i < info.variants.size(); i++) {
                    Variant variant = info.variants.get(i);
                    if (variant.size != reference.size) {
                        String message = String.format(Locale.US,
                                "Array size mismatch: %1$d items in %2$s, %3$d in %4$s",
                                variant.size,
                                variant.configuration.isEmpty() ? "default" : variant.configuration,
                                reference.size,
                                reference.configuration.isEmpty() ? "default" : reference.configuration);
                        context.report(ISSUE, variant.handle.resolve(), message);
                    }
                }
            }
        }

        mArrays.clear();
    }

    private static int countItems(Element array) {
        int count = 0;
        NodeList children = array.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static String getConfigurationName(XmlContext context) {
        FolderConfiguration config = context.getFolderConfiguration();
        if (config == null) {
            return "";
        }

        LocaleQualifier locale = config.getLocaleQualifier();
        if (locale != null && locale.hasLanguage()) {
            return locale.getTag();
        }

        return config.getQualifierString();
    }

    private static class ArrayInfo {
        final String name;
        final List<Variant> variants = new ArrayList<>();

        ArrayInfo(String name) {
            this.name = name;
        }
    }

    private static class Variant {
        final String configuration;
        final int size;
        final Location.Handle handle;

        Variant(String configuration, int size, Location.Handle handle) {
            this.configuration = configuration;
            this.size = size;
            this.handle = handle;
        }
    }
}