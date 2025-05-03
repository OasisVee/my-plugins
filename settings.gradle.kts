rootProject.name = "my-plugins"

// This file sets what projects are included. Every time you add a new project, you must add it
// to the includes below.

// Plugins are included like this
include("AnimeImageFetch")
include(":CatUITH")
include("Sed")
// Explicitly set the project directory for CatUITH
project(":CatUITH").projectDir = File(rootDir, "CatUITH")


