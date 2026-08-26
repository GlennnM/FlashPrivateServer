# BMC Server
### Building
Requires JDK 17+ and git. Use git bash or powershell if on windows.

1. `git clone https://github.com/GlennnM/FlashPrivateServer ; cd FlashPrivateServer/bmc`
2. (WINDOWS) `./compile_bmc.bat` (OTHER) `./compile_bmc.sh`
3. `git clone https://github.com/GlennnM/Hydar-Web-Server ; cd Hydar-Web-Server`
4. `cp -R ../lib . ; cp -R ../classes .`
5. (WINDOWS) `./compile_hydar.bat ../flash.properties`<br>
 (OTHER) `./compile_hydar.sh ../flash.properties`

Setting up client ([FPS 4.0+ client](https://github.com/GlennnM/FlashPrivateServer/releases/latest))
- With this client you can simply select "LOCAL" then "LOG IN" from the server selector in the game options(gear in the bottom right corner on the title screen), or use the fiddler rule (`regex:^https://flash.hydar.xyz/` -> `http://localhost:5572/`).

Setting up client (vanilla NK archive):
- Replace url in BMC SWF(https://web-monkey-city.ninjakiwi.com/ -> http://localhost:5572)
- or just play the game with a fiddler rule active that performs the same redirect, i.e.:<br> (`regex:^https://web-monkey-city.ninjakiwi.com/` -> `http://localhost:5572/`)
- To enable events, use another fiddler rule <br>(`regex:^https://static-api.ninjakiwi.com/nkapi/skusettings/99d5c454171a3f5027a0563eb784a366.json` -> `http://localhost:5572/99d5c454171a3f5027a0563eb784a366.json`)

### Status

**in progress:** 
- Save server. More details will be added later; it is not usable or testable yet.

**working:** 
- handshake (login) - can use NK auth if enabled
- core data (bloonstones, MK, etc)
- city data (tiles, xp/level, etc)
- CT
- MvM
- client(archive mod) + server switcher in game settings
- crates
- MvM bots

eventually: starting city based on tiles achievements, support for different storage backend, save transfer of some kind, new tile format?



