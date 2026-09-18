# EventRestore

Paper 1.20.1 / Java 17.

Commands:
- `/event restore` saves the player's inventory, armor and offhand, then clears them.
- `/event claim` opens a private GUI where the player can reclaim saved items individually.
- `/event admin <player>` lets an admin inspect a player's saved inventory.
- `/event admin restore <player>` restores the saved inventory to an online player.

Permissions:
- `eventrestore.use`
- `eventrestore.admin`

Build:
Push this repository to GitHub. GitHub Actions builds the JAR automatically.
Download it from the workflow's Artifacts section.
