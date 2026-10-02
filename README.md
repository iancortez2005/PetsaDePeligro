# Petsa de Peligro

A 30-day budget survival game about a student living alone in the city:
pay the rent and bills, keep your Hunger, Stress, Sickness and Academics
in check, and save enough for the trip home. Java Swing, built with
NetBeans (Ant).

## Playing it

Download `Petsa de Peligro-<version>.zip` from the
[Releases](https://github.com/iancortez2005/PetsaDePeligro/releases) page,
unzip it anywhere and double-click `Petsa de Peligro.exe`. Java comes inside
the folder, so nothing else needs to be installed (Windows 10/11, 64-bit).
Windows may warn that the app is from an unknown publisher: click
**More info > Run anyway**.

Saves, the leaderboard and settings are kept in `%APPDATA%\Petsa de Peligro`.

## Running it from the source

Open the folder as a project in NetBeans and press Run, or build the jar
with Clean and Build and run `java -jar dist/PetsaDePeligro2.jar`. The project
is set to Java 25 (the JDK that comes with NetBeans).

To make the standalone version, run `tools/build-app.bat`. It packages the
game with a trimmed copy of Java (using the JDK's jpackage) and writes the
zip to `release/`. The app icon is `tools/app-icon.ico`.

To jump straight into a test situation (the ending, Rent Day, 100% Stress,
a rainy day...), run `src/petsa/demo/ScenarioLauncher.java`. Test games use
their own save and leaderboard files.

## Credits

The pixel font is Press Start 2P by CodeMan38, used under the SIL Open Font
License 1.1 (see `src/petsa/ui/resources/PressStart2P-OFL.txt`).

## Branches

- **main**: the game with all its comments, for reference and learning.
- **no-comments**: the same game with every comment removed. It is
  generated from main, never edited by hand.

To update the no-comments branch after changing main, commit and push main,
then run `tools/update-no-comments.bat`. It rebuilds the copy in
`../PetsaDePeligro2-NoComments` with `tools/StripComments.java`, then commits
and pushes it.
