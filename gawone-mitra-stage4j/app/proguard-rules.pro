# GAWONE Mitra Stage 4I release hardening.
# Keep only entry points referenced from Android manifests / Firebase callbacks.
-keep class site.garsyanimultiusaha.gawone.mitra.GawoneMessagingService { *; }
-keep class site.garsyanimultiusaha.gawone.mitra.PartnerPresenceService { *; }

# Keep useful line information for crash fingerprints without exposing local variables.
-keepattributes SourceFile,LineNumberTable

# JSON payloads are parsed explicitly; no broad model keep rules are required.
