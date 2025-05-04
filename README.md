# Plugins

## [⚠️NSFW⚠️] AnimeImageFetch
A version of Nekoslife that doesn't use the nekoslife api (hence the name change) it uses [waifu.pics](https://waifu.pics) as the api so there is some NSFW categories
- command is /animefetch

## CatUITH

Fork of UITH, originally developed by scrazzz, with a primary focus on Catbox functionality. Support for additional hosting services has been preserved. I will update the README for CatUITH to document these modifications when time permits.

### what was changed from the original

- added a default json config for the fetching that is Catbox
- added a userhash option to the settings to set the userhash for Catbox
- added a reset button to reset any user added json for the fetching to default
- added a custom theme to the settings page (trying to implement a toggle for it in case the user wants to use the default theme)
- added a setting to change the timeout for uploading
- album functionality for Catbox (/cuith album to start album mode, /cuith finishalb to finish album mode and get the album link, /cuith cancelalb to cancel album mode without making an album)


## [Sed](https://gitlab.com/Grzesiek11/sed-aliucord-plugin)

Port of the original Sed plugin created by Grzesiek11. I made it here primarily to enable case-insensitive matching for the 's' command, as I encountered difficulties building it on GitLab.
