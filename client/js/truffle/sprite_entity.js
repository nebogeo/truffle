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

truffle.sprite_entity=function(world, pos, t, cmb, viz) {
    truffle.entity.call(this,world,pos);

    this.spr = new truffle.sprite(new truffle.vec2(this.pos.x,this.pos.y),t,cmb,viz);
    this.children=[];

    //this.spr.set_depth(this.depth);
    world.add_sprite(this.spr);
    //this.hide(!viz);
}

truffle.sprite_entity.prototype=
    inherits_from(truffle.entity,truffle.sprite_entity);

truffle.sprite_entity.prototype.add_child=function(world,child) {
    world.add_sprite(child);
    child.update_parent_tx(this.spr.transform);
    this.children.push(child);
}


truffle.sprite_entity.prototype.destroy=function(world) {
    truffle.entity.prototype.destroy.call(this,world);
    world.remove_sprite(this.spr);
    this.children.forEach(function(child) {
        world.remove_sprite(child);
    });
}

truffle.sprite_entity.prototype.update=function(time,delta,world) {
    truffle.entity.prototype.update.call(this,time,delta,world);
    this.spr.set_pos(new truffle.vec2(this.pos.x,this.pos.y));
    var that=this;
    this.children.forEach(function(child) {
        child.update_parent_tx(that.spr.transform);
    });
}

truffle.sprite_entity.prototype.on_sort_scene=function(world, order) {
    this.spr.set_depth(order++);
    this.children.forEach(function(child) {
        child.set_depth(order++);
    });
    return order;
}


