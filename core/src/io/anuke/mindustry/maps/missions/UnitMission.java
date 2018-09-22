package io.anuke.mindustry.maps.missions;

import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.GameMode;
import io.anuke.ucore.util.Bundles;

public class UnitMission implements Mission{
    private final UnitType type;

    public UnitMission(UnitType type){
        this.type = type;
    }

    @Override
    public boolean isComplete(){
        for(BaseUnit unit : Vars.unitGroups[Vars.defaultTeam.ordinal()].all()){
            if(unit.getType() == type){
                return true;
            }
        }
        return false;
    }

    @Override
    public String displayString(){
        return Bundles.format("text.mission.unit", type.localizedName());
    }

    @Override
    public GameMode getMode(){
        return GameMode.noWaves;
    }
}