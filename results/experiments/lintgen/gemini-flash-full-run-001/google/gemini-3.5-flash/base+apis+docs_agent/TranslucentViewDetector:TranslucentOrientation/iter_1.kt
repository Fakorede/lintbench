val targetSdk = try { context.project.targetSdkVersion.apiLevel } catch (e: Exception) { 0 }
if (targetSdk in 2..25) return