package io.anuke.mindustry.world.blocks.power;

import com.badlogic.gdx.math.Vector2;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Layer;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.PowerBlock;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

import static io.anuke.mindustry.Vars.*;

public class PowerNode extends PowerBlock{
    public static final float thicknessScl = 0.7f;
    public static final float flashScl = 0.12f;

    //last distribution block placed
    private static int lastPlaced = -1;

    protected Translator t1 = new Translator();
    protected Translator t2 = new Translator();

    protected float laserRange = 6;
    protected float powerSpeed = 0.5f;
    protected int maxNodes = 3;

    public PowerNode(String name){
        super(name);
        expanded = true;
        layer = Layer.power;
        powerCapacity = 5f;
        configurable = true;
        consumesPower = false;
        outputsPower = false;
    }

    @Remote(targets = Loc.both, called = Loc.server, forward = true)
    public static void linkPowerNodes(Player player, Tile tile, Tile other){
        if(tile.entity.power == null) return;

        TileEntity entity = tile.entity();

        if(!entity.power.links.contains(other.packedPosition())){
            entity.power.links.add(other.packedPosition());
        }

        if(other.getTeamID() == tile.getTeamID()){

            if(!other.entity.power.links.contains(tile.packedPosition())){
                other.entity.power.links.add(tile.packedPosition());
            }
        }

        entity.power.graph.add(other.entity.power.graph);
    }

    @Remote(targets = Loc.both, called = Loc.server, forward = true)
    public static void unlinkPowerNodes(Player player, Tile tile, Tile other){
        if(tile.entity.power == null) return;

        TileEntity entity = tile.entity();

        entity.power.links.removeValue(other.packedPosition());

        if(other.block() instanceof PowerNode){
            other.entity.power.links.removeValue(tile.packedPosition());
        }

        //clear all graph data first
        PowerGraph tg = entity.power.graph;
        tg.clear();
        //reflow from this point, covering all tiles on this side
        tg.reflow(tile);

        //create new graph for other end
        PowerGraph og = new PowerGraph();
        //reflow from other end
        og.reflow(other);
    }

    @Override
    public void setBars(){
    }

    @Override
    public void playerPlaced(Tile tile){
        Tile before = world.tile(lastPlaced);
        if(linkValid(tile, before) && before.block() instanceof PowerNode){
            Call.linkPowerNodes(null, tile, before);
        }

        lastPlaced = tile.packedPosition();
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.powerRange, laserRange, StatUnit.blocks);
        stats.add(BlockStat.powerTransferSpeed, powerSpeed * 60 / 2f, StatUnit.powerSecond); //divided by 2 since passback exists
    }

    @Override
    public void update(Tile tile){
        tile.entity.power.graph.update();
    }

    @Override
    public boolean onConfigureTileTapped(Tile tile, Tile other){
        TileEntity entity = tile.entity();
        other = other.target();

        Tile result = other;

        if(linkValid(tile, other)){
            if(linked(tile, other)){
                threads.run(() -> Call.unlinkPowerNodes(null, tile, result));
            }else if(entity.power.links.size < maxNodes){
                threads.run(() -> Call.linkPowerNodes(null, tile, result));
            }
            return false;
        }
        return true;
    }

    @Override
    public void drawSelect(Tile tile){
        super.drawSelect(tile);

        Draw.color(Palette.power);
        Lines.stroke(1f);

        Lines.poly(Edges.getPixelPolygon(laserRange), tile.worldx() - tilesize / 2, tile.worldy() - tilesize / 2, tilesize);

        Draw.reset();
    }

    @Override
    public void drawConfigure(Tile tile){
        TileEntity entity = tile.entity();

        Draw.color(Palette.accent);

        Lines.stroke(1f);
        Lines.square(tile.drawx(), tile.drawy(),
                tile.block().size * tilesize / 2f + 1f + Mathf.absin(Timers.time(), 4f, 1f));

        Lines.stroke(1f);

        Lines.poly(Edges.getPixelPolygon(laserRange), tile.worldx() - tilesize / 2, tile.worldy() - tilesize / 2, tilesize);

        Draw.color(Palette.power);

        for(int x = (int) (tile.x - laserRange); x <= tile.x + laserRange; x++){
            for(int y = (int) (tile.y - laserRange); y <= tile.y + laserRange; y++){
                Tile link = world.tile(x, y);
                if(link != null) link = link.target();

                if(link != tile && linkValid(tile, link, false)){
                    boolean linked = linked(tile, link);
                    Draw.color(linked ? Palette.place : Palette.breakInvalid);

                    Lines.square(link.drawx(), link.drawy(),
                            link.block().size * tilesize / 2f + 1f + (linked ? 0f : Mathf.absin(Timers.time(), 4f, 1f)));

                    if((entity.power.links.size >= maxNodes || (link.block() instanceof PowerNode && link.entity.power.links.size >= ((PowerNode) link.block()).maxNodes)) && !linked){
                        Draw.color();
                        Draw.rect("cross-" + link.block().size, link.drawx(), link.drawy());
                    }
                }
            }
        }

        Draw.reset();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Draw.color(Palette.placing);
        Lines.stroke(1f);

        Lines.poly(Edges.getPixelPolygon(laserRange), x * tilesize - tilesize / 2, y * tilesize - tilesize / 2, tilesize);

        Draw.reset();
    }

    @Override
    public void drawLayer(Tile tile){
        if(!Settings.getBool("lasers")) return;

        TileEntity entity = tile.entity();

        Draw.color(Palette.powerLaserFrom, Palette.powerLaserTo, 0f * (1f - flashScl) + Mathf.sin(Timers.time(), 1.7f, flashScl));

        for(int i = 0; i < entity.power.links.size; i++){
            Tile link = world.tile(entity.power.links.get(i));
            if(linkValid(tile, link)) drawLaser(tile, link);
        }

        Draw.color();
    }

    protected boolean linked(Tile tile, Tile other){
        return tile.entity.power.links.contains(other.packedPosition());
    }

    protected boolean linkValid(Tile tile, Tile link){
        return linkValid(tile, link, true);
    }

    protected boolean linkValid(Tile tile, Tile link, boolean checkMaxNodes){
        if(!(tile != link && link != null && link.block().hasPower) || tile.getTeamID() != link.getTeamID()) return false;

        if(link.block() instanceof PowerNode){
            TileEntity oe = link.entity();

            return Vector2.dst(tile.drawx(), tile.drawy(), link.drawx(), link.drawy()) <= Math.max(laserRange * tilesize,
                    ((PowerNode) link.block()).laserRange * tilesize) - tilesize / 2f
                    + (link.block().size - 1) * tilesize / 2f + (tile.block().size - 1) * tilesize / 2f &&
                    (!checkMaxNodes || (oe.power.links.size < ((PowerNode) link.block()).maxNodes || oe.power.links.contains(tile.packedPosition())));
        }else{
            return Vector2.dst(tile.drawx(), tile.drawy(), link.drawx(), link.drawy())
                    <= laserRange * tilesize - tilesize / 2f + (link.block().size - 1) * tilesize;
        }
    }

    protected void drawLaser(Tile tile, Tile target){
        float x1 = tile.drawx(), y1 = tile.drawy(),
                x2 = target.drawx(), y2 = target.drawy();

        float angle1 = Angles.angle(x1, y1, x2, y2);
        float angle2 = angle1 + 180f;

        t1.trns(angle1, tile.block().size * tilesize / 2f + 1f);
        t2.trns(angle2, target.block().size * tilesize / 2f + 1f);

        Shapes.laser("laser", "laser-end", x1 + t1.x, y1 + t1.y,
                x2 + t2.x, y2 + t2.y, thicknessScl);
    }

}
