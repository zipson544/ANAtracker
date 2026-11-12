# AnaTracker (Fabric 1.21.4)

Moduł AFK: tpa do osób piszących na czacie, po teleporcie skan graczy pod kątem itemów (domyślnie netherite).

- `/anatracker` – GUI, `/anatracker on|off`, `/anatracker ban|unban <nick>`
- Ctrl+A w GUI – zakładka ITEMY (wstawia item z ręki), Enter dodaje
- Config: `config/anatracker.json` (regexy czatu `chatRegex` / `acceptRegex`, `tpaCommand`)
- Build: push na GitHub -> Actions -> artifact `AnaTracker-jar`  (lokalnie: `gradle build`, Java 21)
