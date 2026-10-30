# Admin Rank (Fabric, Minecraft 26.3)

Operators get an animated `[ADMIN]` tag in the tab list and above their head.
The red letters have a white shimmer that sweeps across them. Vanilla clients see it
(it uses a scoreboard team prefix), and it works in singleplayer, LAN and servers.

- Gets the tag: anyone with op level 2 or higher. Use `/op name` and `/deop name`.
- Players already in another team are left alone.
- Remove it with `/team remove adminrank`.

Build: upload to GitHub, run the "build" workflow, download artifact `admin-rank-jar`.
Install: put the jar and Fabric API in `mods/`.
