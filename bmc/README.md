# BMC Server
### Building
Requires jdk17 and git. Use git bash or powershell if on windows.

1. `git clone https://github.com/GlennnM/FlashPrivateServer ; cd FlashPrivateServer/bmc`
2. `git clone https://github.com/GlennnM/Hydar-Web-Server ; cd Hydar-Web-Server`
3. `cp -R ../lib .`
4. (WINDOWS) `./compile_hydar.bat ../flash.properties`<br>
 (OTHER) `./compile_hydar.sh ../flash.properties`

To make client - either replace url in BMC SWF(https://web-monkey-city.ninjakiwi.com/ -> http://localhost:5572)<br>or just play the game with a fiddler rule active that performs the same redirect, i.e.:<br> (`regex:^https://web-monkey-city.ninjakiwi.com/` -> `http://localhost:5572/`)

Event generation is included - to enable it use another fiddler rule <br>(`regex:^https://static-api.ninjakiwi.com/nkapi/skusettings/99d5c454171a3f5027a0563eb784a366.json` -> `http://localhost:5572/99d5c454171a3f5027a0563eb784a366.json`)

effect on achievements and stuff isn't entirely known(most likely your tiles ones will be reset), use an alt if you can!!!

### Status

**working:** 
- handshake (login) - can use NK auth if enabled
- core data (bloonstones, MK, etc)
- city data (tiles, xp/level, etc)

**not implemented:** 

- MvM
- CT
- crates
- starting city based on tiles achievements?
- performance(some things like tile format are very bad)
- deployment *need to move leaderboards and stuff to https since this has auth
- client *need to add NKMultiArchive into the main script to make it appear
- maybe a beta with a swf or something on a test server before that
- session persistence, maintenance/other server msgs?

eventually: support for different storage backend, save transfer of some kind(ideally client should let you switch servers on title screen to see if nk one is alive), pvp bots

