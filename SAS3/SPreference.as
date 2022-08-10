//"example code" for how singleplayer games might work
package §[Q§
{
   import § 4§.§%§;
   import § 4§.§+0§;
   import § 4§.§=?§;
   import commands.AttackCmd;
   import commands.BarriCmd;
   import commands.Command;
   import commands.DeathCmd;
   import commands.EndCmd;
   import commands.HealthCmd;
   import commands.HitCmd;
   import commands.JoinCmd;
   import commands.LeaveCmd;
   import commands.LoadCmd;
   import commands.LobbyCmd;
   import commands.MoveCmd;
   import commands.MoveKeyCmd;
   import commands.MsgCmd;
   import commands.PowerupCmd;
   import commands.RaiseCmd;
   import commands.ReviveCmd;
   import commands.SentryCmd;
   import commands.SpawnCmd;
   import commands.StartCmd;
   import commands.TargetCmd;
   import commands.TurnCmd;
   import commands.WaveEndCmd;
   import commands.WaveStrtCmd;
   import flash.events.TimerEvent;
   import flash.utils.Dictionary;
   import flash.utils.Timer;
   import flash.utils.getTimer;
   
   public final class § J§
   {
      
      public static const NUM_PLAYERS:int = 1;
      
      private static const §'7§:int = 10;
      
      private static const §4§:Number = 0.2;
      
      private static const §`O§:Number = 0.3333333333;
      
      private static const §%Q§:Number = 2;
      
      private static const §4"§:int = 400;
      
      private static const §=%§:Number = 0.5;
      
      private static const §6J§:int = 5000;
      
      private static const §6=§:int = 2500;
      
      private static const §="§:int = 0;
      
      private static const §5F§:int = 60000;
      
      private static const §1M§:int = 5000;
      
      private static const §8?§:int = 30000;
      
      private static const §1D§:Array = [0,0,0,0,1,5,9,15,22,30,38];
      
      private static const §0O§:Boolean = false;
       
      
      private var xt:§%>§;
      
      private var type:GameType;
      
      private var §#H§:§?O§;
      
      private var player:§]+§;
      
      private var nightmare:Boolean;
      
      private var zombies:Array;
      
      private var SBEmult:Number;
      
      private var §=L§:int;
      
      private var §,L§:int;
      
      private var § I§:int;
      
      private var §[D§:int;
      
      private var §5-§:Number;
      
      private var §]?§:Number;
      
      private var started:Boolean;
      
      private var §9T§:Boolean;
      
      private var §8L§:Boolean;
      
      private var §9M§:Timer;
      
      private var §60§:Timer;
      
      private var §9R§:Timer;
      
      private var startTime:int;
      
      private var §3I§:int;
      
      public function § J§(param1:§%>§)
      {
         super();
         this.xt = param1;
      }
      
      public final function destroy() : void
      {
         if(this.player != null)
         {
            this.player.xp.§!9§();
         }
         if(this.§9M§ != null)
         {
            this.§9M§.stop();
         }
         if(this.§60§ != null)
         {
            this.§60§.stop();
         }
         if(this.§9R§ != null)
         {
            this.§9R§.stop();
         }
      }
      
      public final function join(param1:JoinCmd) : void
      {
         this.type = GameType.ONSLAUGHT;
         this.nightmare = false;
         var _loc2_:String = param1.myPlayerName;
         var _loc3_:int = param1.myPlayerRank;
         this.player = new §]+§();
         this.player.number = 0;
         this.player.name = _loc2_;
         this.player.rank = _loc3_;
         var _loc4_:LobbyCmd;
         (_loc4_ = new LobbyCmd()).success = true;
         _loc4_.numPlayers = NUM_PLAYERS;
         _loc4_.type = this.type;
         _loc4_.myPlayerNum = this.player.number;
         _loc4_.playerNames[0] = this.player.name;
         _loc4_.playerRanks[0] = this.player.rank;
         _loc4_.ready[0] = this.player.ready;
         this.send(_loc4_);
         this.load(_loc4_,param1.mapNum);
      }
      
      public final function leave(param1:LeaveCmd) : void
      {
         this.player = null;
      }
      
      public final function §1L§(param1:StartCmd) : void
      {
         this.player.ready = true;
         var _loc2_:LobbyCmd = new LobbyCmd();
         _loc2_.success = true;
         _loc2_.numPlayers = NUM_PLAYERS;
         _loc2_.type = this.type;
         _loc2_.myPlayerNum = this.player.number;
         _loc2_.playerNames[0] = this.player.name;
         _loc2_.playerRanks[0] = this.player.rank;
         _loc2_.ready[0] = this.player.ready;
         this.send(_loc2_);
         this.start();
      }
      
      private final function load(param1:LobbyCmd, param2:int) : void
      {
         var mapDef:§0§ = null;
         var lobby:LobbyCmd = param1;
         var mapNum:int = param2;
         try
         {
            mapDef = §0§.fromInt(mapNum);
         }
         catch(e:Error)
         {
         }
         if(mapDef == null)
         {
            mapDef = §0§.random();
         }
         this.§#H§ = new §?O§(mapDef,this.xt);
         var loadCmd:LoadCmd = new LoadCmd();
         loadCmd.success = true;
         loadCmd.numPlayers = lobby.numPlayers;
         loadCmd.myPlayerNum = lobby.myPlayerNum;
         loadCmd.playerNames = lobby.playerNames;
         loadCmd.playerRanks = lobby.playerRanks;
         loadCmd.privateID = lobby.privateID;
         loadCmd.ready = lobby.ready;
         loadCmd.type = this.type;
         loadCmd.mapURL = mapDef.url;
         loadCmd.nightmare = this.nightmare;
         this.send(loadCmd);
      }
      
      private final function start() : void
      {
         if(this.started)
         {
            return;
         }
         this.zombies = [];
         this.§=L§ = this.§+"§(this.player.rank);
         this.§,L§ = 0;
         if(this.nightmare)
         {
            this.SBEmult = (1 + this.player.rank / 10) / §%Q§ * 10;
         }
         else
         {
            this.SBEmult = (1 + this.player.rank / 10) / §%Q§;
         }
         this.§]?§ = 0;
         this.§5-§ = 0;
         this.started = true;
         this.§8L§ = false;
         var _loc1_:int = §9§.MAX_HP * this.SBEmult * this.SBEmult;
         this.§#H§.§0$§(_loc1_);
         this.§9M§ = new Timer(1,1);
         this.§60§ = new Timer(§6J§,1);
         this.§9R§ = new Timer(§8?§,1);
         this.§9M§.addEventListener(TimerEvent.TIMER_COMPLETE,this.§"A§);
         this.§60§.addEventListener(TimerEvent.TIMER_COMPLETE,this.§'R§);
         this.§9R§.addEventListener(TimerEvent.TIMER_COMPLETE,this.§#,§);
         this.§9M§.start();
         this.§;H§();
         var _loc2_:StartCmd = new StartCmd();
         _loc2_.SBEmult = this.SBEmult;
         _loc2_.barriHp = _loc1_;
         this.§!N§(_loc2_);
         this.send(_loc2_);
      }
      
      private final function end(param1:Boolean) : void
      {
         var _loc2_:Number = NaN;
         this.§9T§ = true;
         switch(this.§#H§.def)
         {
            case §0§.§;#§:
               _loc2_ = 1;
               break;
            case §0§.§!3§:
               _loc2_ = 1;
               break;
            default:
               _loc2_ = 1;
         }
         this.player.xp.value *= _loc2_;
         if(this.player.§1K§ > §4"§)
         {
            this.player.§1K§ = §4"§;
         }
         this.player.xp.value += this.player.§1K§;
         if(!param1)
         {
            this.player.xp.value *= 0.3333333;
         }
         var _loc3_:int = Math.round(this.player.xp.value * §4§);
         var _loc4_:*;
         if((_loc4_ = this.player.rank >= Rank.MAX_RANK_VALUE_STD) && !this.nightmare)
         {
            this.player.xp.value = 0;
         }
         var _loc5_:EndCmd;
         (_loc5_ = new EndCmd()).win = param1;
         _loc5_.names[0] = this.player.name;
         _loc5_.kills[0] = this.player.kills;
         _loc5_.damage[0] = this.player.damage;
         _loc5_.deaths[0] = this.player.deaths;
         _loc5_.revives[0] = this.player.revives;
         _loc5_.xp[0] = this.player.xp.value;
         _loc5_.cash[0] = _loc3_;
         _loc5_.ranks[0] = this.player.rank;
         this.§!N§(_loc5_);
         this.send(_loc5_);
      }
      
      public final function §9#§() : Boolean
      {
         return this.player != null;
      }
      
      public final function §>A§() : Boolean
      {
         return this.player == null;
      }
      
      public final function §-Q§() : Boolean
      {
         return this.started;
      }
      
      public final function move(param1:MoveCmd) : void
      {
         var _loc2_:MoveKeyCmd = null;
         if(param1 is MoveKeyCmd)
         {
            _loc2_ = MoveKeyCmd(param1);
            this.player.x = _loc2_.x;
            this.player.y = _loc2_.y;
         }
         if(§0O§)
         {
            this.send(param1);
         }
      }
      
      public final function turn(param1:TurnCmd) : void
      {
         if(§0O§)
         {
            this.send(param1);
         }
      }
      
      public final function hit(param1:HitCmd) : void
      {
         var _loc5_:* = null;
         var _loc6_:§3H§ = null;
         var _loc7_:Number = NaN;
         var _loc8_:int = 0;
         var _loc2_:TargetCmd = null;
         var _loc3_:HitCmd = new HitCmd();
         var _loc4_:Boolean = false;
         for(_loc5_ in param1.damages)
         {
            _loc6_ = this.zombies[_loc5_];
            _loc7_ = param1.damages[_loc5_];
            _loc8_ = param1.getInfector(parseInt(_loc5_));
            if(!(_loc6_.dead || _loc6_.hp == 0 || _loc7_ <= 0))
            {
               if(_loc7_ > _loc6_.hp)
               {
                  _loc7_ = _loc6_.hp;
               }
               this.player.damage += _loc7_;
               _loc6_.hp -= param1.damages[_loc5_];
               if(_loc6_.hp <= 0)
               {
                  _loc6_.hp = 0;
                  _loc6_.dead = true;
                  _loc4_ = true;
                  this.player.xp.value += _loc6_.xp;
                  ++this.player.kills;
                  this.§>'§(_loc6_,this.player,param1.time);
               }
               else if(_loc6_.§!F§ != this.player && this.§?7§(_loc6_,this.player))
               {
                  _loc6_.§!F§ = this.player;
                  if(_loc2_ == null)
                  {
                     _loc2_ = new TargetCmd();
                     _loc2_.player = this.player.number;
                  }
                  _loc2_.zombies.push(_loc6_.number);
               }
               _loc3_.addDamage(_loc6_.number,_loc6_.hp);
               if(_loc6_.dead)
               {
                  _loc3_.addDeath(_loc6_.number);
               }
               if(_loc8_ >= 0)
               {
                  _loc3_.addInfector(_loc6_.number,_loc8_);
               }
            }
         }
         _loc3_.time = param1.time;
         this.send(_loc3_);
         if(_loc4_)
         {
            this.§7§(param1.time + 1);
         }
         if(_loc2_ != null)
         {
            this.§!N§(_loc2_);
            this.send(_loc2_);
         }
      }
      
      public final function health(param1:HealthCmd) : void
      {
         var _loc2_:HealthCmd = null;
         var _loc3_:DeathCmd = null;
         if(this.§9T§)
         {
            return;
         }
         if(param1.hp <= 0)
         {
            this.player.dead = true;
            ++this.player.deaths;
            _loc3_ = new DeathCmd();
            _loc3_.player = param1.player;
            _loc3_.time = param1.time;
            _loc3_.spawnWait = int.MAX_VALUE;
            _loc2_ = _loc3_;
            this.§36§(param1.time);
            this.end(false);
         }
         else
         {
            this.player.dead = false;
            _loc2_ = param1;
         }
         this.send(_loc2_);
      }
      
      public final function revive(param1:ReviveCmd) : void
      {
      }
      
      public final function message(param1:MsgCmd) : void
      {
      }
      
      public final function §97§(param1:SentryCmd) : void
      {
      }
      
      public final function barricade(param1:BarriCmd) : void
      {
         var _loc2_:§9§ = this.§#H§.barricades[param1.barriNum];
         var _loc3_:int = _loc2_.hp;
         var _loc4_:BarriCmd = _loc2_.§ A§(param1);
         var _loc5_:int;
         if((_loc5_ = _loc2_.hp) - _loc3_ > 80)
         {
            this.player.§1K§ += 30;
         }
      }
      
      public final function §[9§(param1:AttackCmd) : void
      {
         var _loc2_:§3H§ = null;
         this.send(param1);
         if(param1.type == AttackCmd.ATTACK_RAISE)
         {
            _loc2_ = this.zombies[param1.zombieIndex];
            this.§>"§(_loc2_,param1.time);
         }
      }
      
      public final function powerup(param1:PowerupCmd) : void
      {
         this.player.xp.value += 50;
      }
      
      private final function §"A§(param1:TimerEvent) : void
      {
         this.§8L§ = true;
         this.§ I§ = Math.floor(§5F§ / §6=§);
         this.§[D§ = 0;
         this.§]?§ = 0;
         this.§5-§ = 0;
         var _loc2_:WaveStrtCmd = new WaveStrtCmd();
         _loc2_.waveNum = this.§,L§ + 1;
         _loc2_.total = this.§=L§;
         this.§!N§(_loc2_);
         this.send(_loc2_);
         this.§60§.delay = §6J§;
         this.§60§.reset();
         this.§60§.start();
         this.§9R§.delay = §8?§;
         this.§9R§.reset();
         this.§9R§.start();
      }
      
      private final function §'R§(param1:TimerEvent) : void
      {
         var _loc11_:§96§ = null;
         var _loc12_:§96§ = null;
         var _loc13_:Number = NaN;
         var _loc14_:§3H§ = null;
         var _loc15_:SpawnCmd = null;
         var _loc16_:Number = NaN;
         var _loc17_:int = 0;
         var _loc2_:Number = this.§,#§();
         var _loc3_:Array = [];
         var _loc4_:int = 0;
         while(_loc4_ < §3H§.§?"§.length)
         {
            if(!((_loc11_ = §3H§.§?"§[_loc4_]) == §3H§.§,-§ && this.§#H§.def == §0§.§;#§))
            {
               if(_loc11_.§6C§ <= _loc2_)
               {
                  _loc3_.push(_loc11_);
               }
            }
            _loc4_++;
         }
         var _loc5_:Array = this.§-;§(_loc3_);
         var _loc6_:Number;
         var _loc7_:Number = (_loc6_ = this.§-L§(_loc2_)) * §6=§ / 1000;
         this.§5-§ += _loc7_;
         var _loc8_:Number;
         if((_loc8_ = this.§5-§ - this.§]?§) < _loc7_ / 3)
         {
         }
         var _loc10_:Number = 0;
         while(_loc10_ < _loc8_)
         {
            _loc12_ = _loc3_[_loc3_.length - 1];
            _loc16_ = Math.random();
            _loc17_ = 0;
            while(_loc17_ < _loc5_.length)
            {
               if(_loc16_ < _loc5_[_loc17_])
               {
                  _loc12_ = _loc3_[_loc17_];
                  break;
               }
               _loc17_++;
            }
            _loc13_ = ((_loc12_.§%O§ - 1) * §=%§ + 1) * this.SBEmult;
            _loc10_ += _loc13_;
            this.§]?§ += _loc13_;
            (_loc14_ = new §3H§(_loc12_,this.SBEmult)).number = this.zombies.length;
            _loc14_.§!F§ = this.player;
            this.zombies.push(_loc14_);
            (_loc15_ = new SpawnCmd()).player = _loc14_.§!F§.number;
            _loc15_.type = _loc12_.index;
            _loc15_.point = this.§#H§.spawns[Math.floor(Math.random() * this.§#H§.spawns.length)];
            _loc15_.multiplier = this.SBEmult;
            _loc15_.number = _loc14_.number;
            _loc15_.parent = -1;
            this.§!N§(_loc15_);
            this.send(_loc15_);
         }
         ++this.§[D§;
         if(this.§[D§ < this.§ I§)
         {
            this.§?2§();
         }
         else
         {
            this.§7§(this.§70§());
         }
      }
      
      private final function §#,§(param1:TimerEvent) : void
      {
         var _loc9_:Number = NaN;
         var _loc2_:Number = 2 * Math.min(0.083,this.player.rank * 0.0032 + 0.0138);
         var _loc3_:Number = 0 * 2 * _loc2_;
         var _loc4_:Number = 0.5 * (1 - _loc2_ - _loc3_);
         var _loc5_:Number = 0.3 * (1 - _loc2_ - _loc3_);
         var _loc6_:int = Math.floor(Math.random() * this.§#H§.powerups.length);
         var _loc7_:int = this.§#H§.powerups[_loc6_];
         var _loc8_:PowerupCmd;
         (_loc8_ = new PowerupCmd()).point = _loc7_;
         var _loc10_:int = 0;
         var _loc11_:Number;
         if((_loc11_ = Math.random()) < _loc2_)
         {
            _loc8_.type = Powerup.SENTRY;
            _loc9_ = Math.max(1,Math.min(Powerup.SENTRY.subTypes,(this.player.rank - 5) / 15 + 1));
         }
         else if(_loc11_ < _loc3_ + _loc2_)
         {
            _loc8_.type = Powerup.SHIELD;
            _loc9_ = Powerup.SHIELD.subTypes;
         }
         else if(_loc11_ < _loc5_ + _loc3_ + _loc2_)
         {
            _loc8_.type = Powerup.NADES;
            _loc9_ = Powerup.NADES.subTypes;
            _loc9_ = 2;
         }
         else if(_loc11_ < _loc4_ + _loc5_ + _loc3_ + _loc2_)
         {
            _loc8_.type = Powerup.GUN;
            _loc9_ = 10;
            _loc10_ = Math.round(Math.min(this.player.rank + 1,Powerup.GUN.subTypes - _loc9_));
            if(_loc11_ > 0.8)
            {
               _loc11_ = (_loc11_ - 0.8) * 6 + 0.8;
            }
         }
         else
         {
            _loc8_.type = Powerup.CASH;
            _loc9_ = Powerup.CASH.subTypes;
         }
         var _loc12_:int;
         if((_loc12_ = Math.floor(Math.random() * _loc9_) + _loc10_) >= _loc8_.type.subTypes)
         {
            _loc12_ = _loc8_.type.subTypes - 1;
         }
         switch(_loc8_.type)
         {
            case Powerup.GUN:
               _loc8_.item = §%§.S[_loc12_];
               break;
            case Powerup.NADES:
               _loc8_.item = §+0§.S[_loc12_];
               break;
            case Powerup.SENTRY:
               _loc8_.item = §=?§.S[_loc12_];
               break;
            case Powerup.SHIELD:
            case Powerup.AMMO:
            case Powerup.CASH:
               _loc8_.item = null;
         }
         this.§!N§(_loc8_);
         this.send(_loc8_);
      }
      
      private final function §-;§(param1:Array) : Array
      {
         var _loc5_:§96§ = null;
         var _loc6_:Array = null;
         var _loc7_:Number = NaN;
         var _loc8_:int = 0;
         var _loc9_:Number = NaN;
         var _loc2_:int = param1.length;
         var _loc3_:Dictionary = §3H§.§8O§();
         var _loc4_:Number = 0;
         for each(_loc5_ in param1)
         {
            _loc4_ += _loc3_[_loc5_];
         }
         _loc6_ = new Array(_loc2_);
         _loc7_ = 1;
         _loc8_ = _loc2_ - 1;
         while(_loc8_ >= 0)
         {
            _loc6_[_loc8_] = _loc7_;
            _loc5_ = param1[_loc8_];
            _loc9_ = _loc3_[_loc5_] / _loc4_;
            _loc7_ -= _loc9_;
            _loc8_--;
         }
         return _loc6_;
      }
      
      private final function §?2§() : void
      {
         if(this.§]3§(§3H§.§-O§,3))
         {
            this.§60§.delay = §6=§ + (Math.random() * §="§ - §="§ / 2);
         }
         else
         {
            this.§60§.delay = 1;
         }
         this.§60§.reset();
         this.§60§.start();
      }
      
      private final function §7§(param1:int) : void
      {
         if(!this.§8L§)
         {
            return;
         }
         if(this.§[D§ < this.§ I§)
         {
            return;
         }
         if(!this.§]3§(§3H§.§-O§,3))
         {
            this.waveEnd(param1);
         }
      }
      
      private final function waveEnd(param1:int) : void
      {
         var _loc2_:WaveEndCmd = null;
         this.§8L§ = false;
         ++this.§,L§;
         this.§36§(param1);
         this.§+'§();
         if(this.§,L§ >= this.§=L§)
         {
            this.end(true);
         }
         else
         {
            _loc2_ = new WaveEndCmd();
            _loc2_.waveNum = this.§,L§;
            _loc2_.total = this.§=L§;
            this.§!N§(_loc2_);
            this.send(_loc2_);
            this.§9M§.delay = §1M§;
            this.§9M§.reset();
            this.§9M§.start();
         }
      }
      
      private final function §>'§(param1:§3H§, param2:§]+§, param3:int) : void
      {
         var _loc4_:int = 0;
         var _loc5_:§96§ = null;
         var _loc7_:§3H§ = null;
         var _loc8_:SpawnCmd = null;
         switch(param1.def)
         {
            case §3H§.§!1§:
               _loc5_ = §3H§.§@D§;
               _loc4_ = 5;
               break;
            case §3H§.MAMUSHKA1:
               _loc5_ = §3H§.MAMUSHKA2;
               _loc4_ = 2;
               break;
            case §3H§.MAMUSHKA2:
               _loc5_ = §3H§.MAMUSHKA3;
               _loc4_ = 2;
               break;
            case §3H§.MAMUSHKA3:
               _loc5_ = §3H§.MAMUSHKA4;
               _loc4_ = 2;
               break;
            default:
               return;
         }
         var _loc6_:uint = 0;
         while(_loc6_ < _loc4_)
         {
            (_loc7_ = new §3H§(_loc5_,this.SBEmult)).§!F§ = param2;
            _loc7_.number = this.zombies.length;
            this.zombies.push(_loc7_);
            (_loc8_ = new SpawnCmd()).player = _loc7_.§!F§.number;
            _loc8_.type = _loc7_.def.index;
            _loc8_.point = -1;
            _loc8_.multiplier = this.SBEmult;
            _loc8_.number = _loc7_.number;
            _loc8_.parent = param1.number;
            _loc8_.time = param3;
            this.send(_loc8_);
            _loc6_++;
         }
      }
      
      private final function §>"§(param1:§3H§, param2:int) : void
      {
         var _loc5_:§3H§ = null;
         var _loc6_:RaiseCmd = null;
         var _loc7_:Number = NaN;
         var _loc3_:int = Math.ceil(this.player.rank / 4) + 2;
         var _loc4_:uint = 0;
         while(_loc4_ < _loc3_)
         {
            (_loc5_ = new §3H§(§3H§.§"&§,this.SBEmult)).§!F§ = this.player;
            _loc5_.number = this.zombies.length;
            this.zombies.push(_loc5_);
            (_loc6_ = new RaiseCmd()).player = _loc5_.§!F§.number;
            _loc6_.type = _loc5_.def.index;
            _loc6_.point = -1;
            _loc6_.multiplier = this.SBEmult;
            _loc6_.number = _loc5_.number;
            _loc6_.parent = param1.number;
            _loc7_ = 2 * Math.PI / _loc3_ * _loc4_;
            _loc6_.xOffset = Math.round(Math.cos(_loc7_) * 100);
            _loc6_.yOffset = Math.round(Math.sin(_loc7_) * 100);
            _loc6_.time = param2;
            this.send(_loc6_);
            _loc4_++;
         }
      }
      
      private final function §+'§() : void
      {
         var _loc2_:§3H§ = null;
         var _loc1_:HitCmd = new HitCmd();
         for each(_loc2_ in this.zombies)
         {
            _loc1_.addDamage(_loc2_.number,_loc2_.hp);
            _loc1_.addDeath(_loc2_.number);
         }
         this.§!N§(_loc1_);
         this.send(_loc1_);
      }
      
      private final function §-L§(param1:Number) : Number
      {
         var _loc2_:Number = NaN;
         var _loc3_:Number = NaN;
         var _loc4_:Number = NaN;
         var _loc5_:Number = NaN;
         switch(this.§#H§.def)
         {
            case §0§.§;#§:
               _loc2_ = 0.65;
               break;
            case §0§.§!3§:
            case §0§.§3&§:
               _loc2_ = 0.75;
               break;
            case §0§.§,$§:
               _loc2_ = 1.1;
               break;
            default:
               _loc2_ = 1;
         }
         if(this.nightmare)
         {
            _loc3_ = 2.5;
         }
         else
         {
            _loc3_ = 0.9;
         }
         if(param1 > 45)
         {
            _loc4_ = 45;
            _loc5_ = param1 - 45;
         }
         else
         {
            _loc4_ = param1;
            _loc5_ = 0;
         }
         var _loc6_:Number = 10 - (_loc4_ - 10) / 7;
         var _loc7_:Number = _loc4_ / 18;
         var _loc8_:Number = Math.pow(_loc6_,_loc7_) * 1.2;
         var _loc9_:Number = Math.pow(_loc5_,1.5) * 5.2;
         return (_loc8_ + _loc9_) / 4 * NUM_PLAYERS * _loc2_ * _loc3_;
      }
      
      private final function §,#§() : Number
      {
         return this.player.rank + (this.§[D§ + this.§ I§ * this.§,L§) / (this.§ I§ * this.§=L§) * §'7§ * ((this.§=L§ - 4) / 12 + 1);
      }
      
      private final function §+"§(param1:Number) : int
      {
         var _loc3_:int = 0;
         var _loc2_:int = 0;
         while(_loc2_ < §1D§.length)
         {
            _loc3_ = §1D§[_loc2_];
            if(param1 < _loc3_)
            {
               _loc2_--;
               break;
            }
            _loc2_++;
         }
         return _loc2_;
      }
      
      private final function §]3§(param1:§96§, param2:int) : Boolean
      {
         var _loc6_:§3H§ = null;
         var _loc3_:Number = param1.§%O§ * param2;
         var _loc4_:Number = 0;
         var _loc5_:int = this.zombies.length - 1;
         while(_loc5_ >= 0)
         {
            if(!(_loc6_ = this.zombies[_loc5_]).dead)
            {
               if((_loc4_ += _loc6_.def.§%O§) > _loc3_)
               {
                  return true;
               }
            }
            _loc5_--;
         }
         return false;
      }
      
      public final function §?7§(param1:§3H§, param2:§]+§) : Boolean
      {
         return (param1.number & 1 ^ param2.number & 1) != 0;
      }
      
      private final function §;H§() : void
      {
         this.startTime = getTimer();
         this.§3I§ = 0;
      }
      
      private final function §70§() : int
      {
         return getTimer() - this.startTime - this.§3I§;
      }
      
      private final function §!N§(param1:Command) : void
      {
         param1.time = this.§70§();
      }
      
      private final function §36§(param1:int) : void
      {
         var _loc2_:int = this.§70§();
         if(param1 > _loc2_)
         {
            return;
         }
         this.§3I§ += _loc2_ - param1;
      }
      
      private final function send(param1:Command) : void
      {
         this.xt.§'2§(param1);
      }
   }
}
