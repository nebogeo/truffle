// t r u f f l e Copyright (C) 2012 FoAM vzw   \_\ __     /\
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

//////////////////////////////////////////////////////////////////////

function build_list(n,f) {
    var arr=[];
    for (var i=0; i<n; i++) arr.push(f(i));
    return arr;
}

function game(world) {
    this.world=world;
    this.entities=[];
    this.next_pull_time=0;
    this.check_zizim_time=0;
    this.world.canvas_state.bg_colour = "#000000";
    
    var that=this;
    build_list(50,function(i) {return i;}).forEach(function(id) {
        var t=new truffle.sprite_entity(
            that.world,
            new truffle.vec2(crndf()*200,
                             crndf()*200,10),
            "../game/images/blip.png",false);
        t.needs_update=true;
        t.speed=0.5;
        t.spr.expand_bb=0;
 //       t.spr.rotate(rndf());
        t.every_frame = function (){
            t.spr.rotate(0.02);
            if (rndf()>0.999) {
                t.move_to(world,crndvec2().mul(200));
                new truffle.particles_entity(world,
                                             t.spr.pos, 
                                             "../game/images/particle.png",
                                             10,"one-shot");
            }
        };

        t.spr.mouse_over(function() {
            t.move_to(world,crndvec2().mul(200));
//            var p=new truffle.particles_entity(world,
//                                               t.spr.pos, 
//                                               "../game/images/particle.png",
//                                               10,"one-shot");
            
        });

        that.entities.push(t);
    });

    var t=new truffle.sprite_entity(
        this.world,
        new truffle.vec3(0,0,-100),
        "../game/images/bg.png",false);

}

game.prototype.update = function(time,delta) {
    this.world.update(time,delta);
}


function game_create() {
    g=new game(truffle.main.world);
    g.logged_in=true;
}

function game_update(time,delta) {
    g.update(time,delta);
}
