# Plugins

A collection of Aliucord plugins I've made or modified. (mostly modified)

---

## AnimeImageFetch ⚠️NSFW⚠️

Replacement for Nekoslife that uses the [waifu.pics](https://waifu.pics) API instead. Includes NSFW categories unlike the original.

- **Command:** `/animefetch`

---

## CatUITH

Fork of UITH (originally by scrazzz) focused on Catbox integration. Still supports other hosting services.

### Changes from original:
- Default config set up for Catbox
- Userhash setting for Catbox uploads
- Reset button to restore default config
- Custom theme (working on a toggle)
- Configurable upload timeout
- Album support:
  - `/cuith album` - start album mode
  - `/cuith finishalb` - finish album and get link
  - `/cuith cancelalb` - cancel album mode
- Upload history support:
  - `/cuith history` - shows history (does from locally so uploaded files from before this addition will not be shown)
  - `/cuith clearhistory` - clears history (can also be used by a button in settings)
  - max history count in settings (Default: 50, setting to 0 disables history)

---

## Sed

Port of [Grzesiek11's Sed plugin](https://gitlab.com/Grzesiek11/sed-aliucord-plugin). Main change is adding case-insensitive matching for the 's' command. Ported it here because building on GitLab was a pain.

---

## Skull

Fork of Moyai that plays Matt Rose screaming "SKULL EMOJI" when you send 💀, react with 💀, or type "skull emoji".
