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

truffle.particles_entity=function(world, pos, t, count, mode) {
    truffle.entity.call(this,world,pos);
    this.particles=new truffle.particles(
        new truffle.vec2(this.pos.x,this.pos.y),t,count,mode);
    world.add_sprite(this.particles);
//    this.particles.draw_bb=true;
    this.particles.expand_bb=100;
    this.needs_update=true;
}

truffle.particles_entity.prototype=
    inherits_from(truffle.entity,truffle.particles_entity);

truffle.particles_entity.prototype.destroy=function(world) {
    world.remove_sprite(this.particles);
}

truffle.particles_entity.prototype.update=function(time, delta, world) {
    truffle.entity.prototype.update.call(this,time,delta,world);
    this.delete_me=this.particles.delete_me;
    this.particles.update(time,delta);
}

truffle.particles_entity.prototype.on_sort_scene=function(world, order) {
    this.particles.set_depth(order++);
    return order;
}


