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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look "
                            + "like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /**
     * Map from likely typo tag names to the correct tag names.
     */
    private static final Map<String, String> TYPOS = new HashMap<>();

    static {
        // Common misspellings of manifest tags
        TYPOS.put("manifest", null); // correct
        TYPOS.put("Manifest", "manifest");
        TYPOS.put("menifest", "manifest");
        TYPOS.put("manifets", "manifest");
        TYPOS.put("manifst", "manifest");
        TYPOS.put("manifiest", "manifest");

        TYPOS.put("application", null); // correct
        TYPOS.put("Application", "application");
        TYPOS.put("aplpication", "application");
        TYPOS.put("applicaiton", "application");
        TYPOS.put("applicaton", "application");
        TYPOS.put("applcation", "application");
        TYPOS.put("aplication", "application");
        TYPOS.put("appliation", "application");

        TYPOS.put("activity", null); // correct
        TYPOS.put("Activity", "activity");
        TYPOS.put("activty", "activity");
        TYPOS.put("actvity", "activity");
        TYPOS.put("activiy", "activity");
        TYPOS.put("activiti", "activity");
        TYPOS.put("acitivity", "activity");
        TYPOS.put("actviity", "activity");

        TYPOS.put("service", null); // correct
        TYPOS.put("Service", "service");
        TYPOS.put("serivce", "service");
        TYPOS.put("sercive", "service");
        TYPOS.put("servce", "service");
        TYPOS.put("sevice", "service");

        TYPOS.put("receiver", null); // correct
        TYPOS.put("Receiver", "receiver");
        TYPOS.put("reciever", "receiver");
        TYPOS.put("reciver", "receiver");
        TYPOS.put("receiever", "receiver");
        TYPOS.put("receivr", "receiver");

        TYPOS.put("provider", null); // correct
        TYPOS.put("Provider", "provider");
        TYPOS.put("provder", "provider");
        TYPOS.put("providor", "provider");
        TYPOS.put("proivder", "provider");

        TYPOS.put("intent-filter", null); // correct
        TYPOS.put("intent-fliter", "intent-filter");
        TYPOS.put("intent-filte", "intent-filter");
        TYPOS.put("intent-filtter", "intent-filter");
        TYPOS.put("intentfilter", "intent-filter");
        TYPOS.put("intent-filter2", null); // not a typo, just wrong

        TYPOS.put("uses-permission", null); // correct
        TYPOS.put("uses-permision", "uses-permission");
        TYPOS.put("use-permission", "uses-permission");
        TYPOS.put("uses-permisson", "uses-permission");
        TYPOS.put("uses-permssion", "uses-permission");
        TYPOS.put("uses-permsision", "uses-permission");
        TYPOS.put("user-permission", "uses-permission");

        TYPOS.put("uses-feature", null); // correct
        TYPOS.put("use-feature", "uses-feature");
        TYPOS.put("uses-feture", "uses-feature");
        TYPOS.put("user-feature", "uses-feature");

        TYPOS.put("uses-sdk", null); // correct
        TYPOS.put("use-sdk", "uses-sdk");
        TYPOS.put("uses-skd", "uses-sdk");
        TYPOS.put("user-sdk", "uses-sdk");

        TYPOS.put("uses-library", null); // correct
        TYPOS.put("use-library", "uses-library");
        TYPOS.put("user-library", "uses-library");

        TYPOS.put("meta-data", null); // correct
        TYPOS.put("metadata", "meta-data");
        TYPOS.put("meta-date", "meta-data");
        TYPOS.put("meta-deta", "meta-data");

        TYPOS.put("action", null); // correct
        TYPOS.put("Action", "action");
        TYPOS.put("acton", "action");
        TYPOS.put("actoin", "action");

        TYPOS.put("category", null); // correct
        TYPOS.put("Category", "category");
        TYPOS.put("catgory", "category");
        TYPOS.put("catagory", "category");
        TYPOS.put("categroy", "category");

        TYPOS.put("data", null); // correct
        TYPOS.put("Data", "data");

        TYPOS.put("permission", null); // correct
        TYPOS.put("Permission", "permission");
        TYPOS.put("permision", "permission");
        TYPOS.put("permisson", "permission");
        TYPOS.put("permssion", "permission");

        TYPOS.put("permission-group", null); // correct
        TYPOS.put("permission-grp", "permission-group");
        TYPOS.put("permision-group", "permission-group");

        TYPOS.put("permission-tree", null); // correct
        TYPOS.put("permision-tree", "permission-tree");

        TYPOS.put("instrumentation", null); // correct
        TYPOS.put("Instrumentation", "instrumentation");
        TYPOS.put("instrumenation", "instrumentation");
        TYPOS.put("instrumentaion", "instrumentation");

        TYPOS.put("supports-screens", null); // correct
        TYPOS.put("support-screens", "supports-screens");
        TYPOS.put("supports-screen", "supports-screens");

        TYPOS.put("compatible-screens", null); // correct
        TYPOS.put("compatable-screens", "compatible-screens");

        TYPOS.put("supports-gl-texture", null); // correct
        TYPOS.put("support-gl-texture", "supports-gl-texture");

        TYPOS.put("grant-uri-permission", null); // correct
        TYPOS.put("grant-uri-permissions", "grant-uri-permission");

        TYPOS.put("path-permission", null); // correct
        TYPOS.put("path-permision", "path-permission");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Correct tags
                "manifest",
                "application",
                "activity",
                "service",
                "receiver",
                "provider",
                "intent-filter",
                "uses-permission",
                "uses-feature",
                "uses-sdk",
                "uses-library",
                "meta-data",
                "action",
                "category",
                "data",
                "permission",
                "permission-group",
                "permission-tree",
                "instrumentation",
                "supports-screens",
                "compatible-screens",
                "supports-gl-texture",
                "grant-uri-permission",
                "path-permission",
                // Typo tags
                "Manifest",
                "menifest",
                "manifets",
                "manifst",
                "manifiest",
                "Application",
                "aplpication",
                "applicaiton",
                "applicaton",
                "applcation",
                "aplication",
                "appliation",
                "Activity",
                "activty",
                "actvity",
                "activiy",
                "activiti",
                "acitivity",
                "actviity",
                "Service",
                "serivce",
                "sercive",
                "servce",
                "sevice",
                "Receiver",
                "reciever",
                "reciver",
                "receiever",
                "receivr",
                "Provider",
                "provder",
                "providor",
                "proivder",
                "intent-fliter",
                "intent-filte",
                "intent-filtter",
                "intentfilter",
                "uses-permision",
                "use-permission",
                "uses-permisson",
                "uses-permssion",
                "uses-permsision",
                "user-permission",
                "use-feature",
                "uses-feture",
                "user-feature",
                "use-sdk",
                "uses-skd",
                "user-sdk",
                "use-library",
                "user-library",
                "metadata",
                "meta-date",
                "meta-deta",
                "Action",
                "acton",
                "actoin",
                "Category",
                "catgory",
                "catagory",
                "categroy",
                "Data",
                "Permission",
                "permision",
                "permisson",
                "permssion",
                "permission-grp",
                "permision-group",
                "permision-tree",
                "Instrumentation",
                "instrumenation",
                "instrumentaion",
                "support-screens",
                "supports-screen",
                "compatable-screens",
                "support-gl-texture",
                "grant-uri-permissions",
                "path-permision"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }

        if (TYPOS.containsKey(tag)) {
            String correct = TYPOS.get(tag);
            if (correct != null) {
                // This is a known typo
                String message = String.format(
                        "Misspelled tag <%1$s>: Did you mean <%2$s>?",
                        tag, correct);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }
}