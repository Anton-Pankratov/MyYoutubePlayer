# Provider integration decisions

| Provider | Catalog source | Playback strategy | Direct URI allowed | Authentication/configuration | Provider player | Current support | Limitations |
| --- | --- | --- | --- | --- | --- | --- | --- |
| YouTube | Official YouTube Data API `search` endpoint (channels and channel videos) | `ProviderControlled`; no official adapter is included yet | No | Data API key for catalog | Yes, when an official adapter is added | Catalog search and channel-video listing | No native playback, stream extraction, download, or webpage scraping |
| Direct | Application-supplied `DirectMediaDescriptor` | Native `VideoPlayerController` | Only an explicitly supplied authorized MP4/HLS/DASH/CDN/signed resource | Application-owned policy | No | Resolver and Android Media3 path | Does not inspect or extract arbitrary webpages; signed URLs are not durable identity |

Catalog metadata and playback authorization are separate. `MediaReference(provider, externalId)` is the durable identity; temporary direct URLs are resolved again rather than persisted as identity.
