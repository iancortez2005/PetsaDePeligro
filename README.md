# Petsa de Peligro

A 30-day budget survival game about a student living alone in the city:
pay the rent and bills, keep your Hunger, Stress, Sickness and Academics
in check, and save enough for the trip home. Java Swing, built with
NetBeans (Ant).

## Running it

Open the folder as a project in NetBeans and press Run, or build the jar
with Clean and Build and run `java -jar dist/PetsaDePeligro2.jar`. The project
is set to Java 25 (the JDK that comes with NetBeans).

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
