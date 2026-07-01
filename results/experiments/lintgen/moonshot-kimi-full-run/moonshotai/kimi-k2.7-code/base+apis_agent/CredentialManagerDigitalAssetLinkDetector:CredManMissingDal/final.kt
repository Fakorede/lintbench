override fun afterCheckEachProject(context: Context) {
    val callLocation = credentialManagerCallLocation ?: return

    if (manifestMetaData == null) {
        context.report(ISSUE, callLocation, "...")
        return
    }

    val (metaDataElement, metaDataLocation) = manifestMetaData!!
    val resource = metaDataElement.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
    if (!resource.startsWith("@string/")) {
        context.report(ISSUE, metaDataLocation, "The asset_statements meta-data must reference a string resource.")
        return
    }

    val stringName = resource.substringAfter("@string/")
    val stringData = stringResources[stringName]
    if (stringData == null) {
        context.report(ISSUE, metaDataLocation, "The string resource @string/$stringName referenced by asset_statements could not be found.")
        return
    }

    val (statement, stringLocation) = stringData
    val includes = ASSET_LINKS_INCLUDE_REGEX.findAll(statement.replace("\\\"", "\"")).toList()
    if (includes.isEmpty()) {
        context.report(ISSUE, stringLocation, "The asset_statements string resource must contain an include entry referencing an assetlinks.json file.")
        return
    }

    val allValid = includes.all { match ->
        val url = match.groupValues[1]
        url.contains(ASSET_LINKS_FILE, ignoreCase = true) && url.contains("://")
    }
    if (!allValid) {
        context.report(ISSUE, stringLocation, "The include entry in the asset_statements string resource must reference a URL to an assetlinks.json file.")
    }
}