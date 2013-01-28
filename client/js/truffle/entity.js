// t r u f f l e Copyright (C) 2010 FoAM vzw   \_\ __     /\
//                                          /\    /_/    / /  
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

truffle.entity=function(world,pos) {
    this.logical_pos = pos;
    this.pos = world.screen_transform(this.logical_pos);
    this.last_pos = world.screen_transform(this.logical_pos);
    this.dest_pos = world.screen_transform(this.logical_pos);
    this.tile_pos = null;
    this.type = "entity";
    this.depth = this.pos.z;
    this.depth_offset = 0;
    this.speed = 0;
    this.move_time = 0;
    this.update_freq = 0;
    this.needs_update = false;
    this.override_pos = false;
    world.add(this);
    this.on_reached_dest=null;
    this.every_frame=null;
    this.delete_me=false;
}

// called by world before destruction (chance to remove sprites)
truffle.entity.prototype.destroy=function(world)
{
}

truffle.entity.prototype.set_tile_pos=function(s) {
    this.tile_pos=s;
}

truffle.entity.prototype.move_to=function(world, pos) {
    this.last_pos = this.pos;
    this.move_time = 0;
    this.logical_pos = pos;
    this.dest_pos = world.screen_transform(this.logical_pos);
}

truffle.entity.prototype.update=function(time, delta, world) {
    if (!this.override_pos)
    {
        if (this.speed==0) {
            this.pos = world.screen_transform(this.logical_pos);
            this.depth = this.pos.z;
        }
        else {
            if (this.move_time<1.0) {
                this.pos = this.last_pos.lerp(this.dest_pos,this.move_time);
                this.move_time += this.speed*delta;
                if (this.on_reached_dest!=null && 
                    this.move_time>=1.0) {
                    // in case we reset from within
                    var tmp=this.on_reached_dest;
                    this.on_reached_dest=null;
                    tmp();
                }
            }
            /*if (this.pos.z<this.dest_pos.z) { 
                this.depth=this.dest_pos.z;
            }*/
            this.depth = this.pos.z;
        }
    }
    if (this.every_frame) this.every_frame();
}


truffle.entity.prototype.get_root=function() {
    return null;
}

truffle.entity.prototype.update_mouse=function(x,y) {
}

truffle.entity.prototype.on_sort_scene=function(world, order) {
    return order;
}

truffle.entity.prototype.hide=function(s) {
    this.hidden=s;
}

truffle.entity.prototype.get_depth=function() {
    return this.depth+this.depth_offset;
}