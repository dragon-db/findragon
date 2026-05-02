# Findragon

Findragon is a modified fork of [Findroid](https://github.com/jarnedemeulemeester/findroid) that is being prepared specifically for DragonDB users.

This repository is currently focused on:

- keeping the existing Android phone and TV codebase buildable
- producing APK artifacts through GitHub Actions
- removing upstream publishing, funding, and community metadata that does not apply to this fork

This repository is not intended to act as the general upstream Findroid project.

## Current scope

- DragonDB-focused fork for private or targeted use
- APK builds only
- full branding, icon, and package rename deferred until later

## Current caveats

- The Android `applicationId` is still `dev.jdtech.jellyfin`
- Installing this build can conflict with or replace an existing Findroid install, depending on signature and install state
- Some in-app visual assets and internal symbol names still use upstream Findroid naming until the later branding pass

## Build artifacts

The retained CI workflow builds debug APK artifacts for both targets:

- `phone`
- `tv`

Store publishing automation from upstream has been removed from the active workflow set in this fork.

## License and attribution

This project remains licensed under [GPLv3](LICENSE).

Findragon is a modified fork of Findroid. See [NOTICE](NOTICE) for the fork notice and modification date.

The logo and launcher assets have not been fully reworked yet. Any remaining upstream visual assets are temporary and will be replaced in a later branding pass.
